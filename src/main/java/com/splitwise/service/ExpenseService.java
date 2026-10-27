package com.splitwise.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import com.splitwise.web.dto.PageResponse;
import com.splitwise.query.Paging;
import com.splitwise.query.ExpenseSpecs;
import com.splitwise.query.ExpenseFilter;
import com.splitwise.domain.RecurringExpense;
import com.splitwise.activity.ExpenseSnapshot;
import com.splitwise.balance.BalanceEffect;
import com.splitwise.activity.ActivityRecorder;
import com.splitwise.domain.Expense;
import com.splitwise.domain.Group;
import com.splitwise.domain.SplitType;
import com.splitwise.domain.User;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ConflictException;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.exception.NotFoundException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.split.SplitParticipant;
import com.splitwise.split.SplitStrategyResolver;
import com.splitwise.web.CallerToday;
import com.splitwise.web.dto.CreateExpenseRequest;
import com.splitwise.web.dto.ExpenseResponse;
import com.splitwise.web.dto.ShareRequest;
import com.splitwise.web.dto.UpdateExpenseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Expenses: create, edit, soft delete and restore.
 *
 * <p>Who may touch an expense: for a group expense, any member of the group (as in Splitwise, where everyone in
 * the group can fix a mistake); for a non-group expense, anyone involved (the payer or a participant).
 *
 * <p>Concurrency: writes to a group expense first lock the group row (see {@link GroupService#lockMemberGroup}),
 * so membership cannot change underneath them. Writes to a non-group expense first lock everyone involved (before
 * and after an edit), the lock a friend settle-up takes, so a settle-up never pays a balance that is changing. Edits carry the version the client last saw; a stale version is
 * a 409, and JPA's {@code @Version} check catches the remaining race at commit.
 */
@Service
@Transactional(readOnly = true)
public class ExpenseService {

    /** Newest expense date first; the id makes the order total, so pages are stable. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "expenseDate", "id");

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;
    private final UserService userService;
    private final SplitStrategyResolver splitStrategies;
    private final Clock clock;
    private final ActivityRecorder activity;
    private final ContactService contacts;
    private final CallerToday today;

    /** Says who without naming anyone: the id may belong to someone the caller has never met. */
    static final String NOT_ALL_MEMBERS = "Someone in it is not a member of this group (they may have just left)";

    public ExpenseService(ExpenseRepository expenseRepository, GroupService groupService,
                          UserService userService, SplitStrategyResolver splitStrategies, Clock clock,
                          ActivityRecorder activity, ContactService contacts, CallerToday today) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.splitStrategies = splitStrategies;
        this.clock = clock;
        this.activity = activity;
        this.contacts = contacts;
        this.today = today;
    }

    /**
     * Validates the request fully (access, membership, split arithmetic) and only then persists the
     * expense and its shares in one transaction.
     */
    @Transactional
    public ExpenseResponse addExpense(Long callerId, CreateExpenseRequest request) {
        // 1. access: group membership (403/404) before anything else; the group row stays locked
        Group group = request.groupId() == null ? null : groupService.lockMemberGroup(request.groupId(), callerId);

        // 2-3. input, split arithmetic and the users involved
        ValidatedSplit split = validate(callerId, group, request.paidById(), request.currency(), request.amount(),
                request.splitType(), request.shares(), Set.of());

        User caller = userService.getById(callerId);
        Expense expense = new Expense(request.description().trim(), request.amount(), split.currency(),
                split.payer(), group, dateOrToday(request.date()), request.splitType(), caller);
        expense.describe(request.category(), request.notes());
        split.addSharesTo(expense);
        Expense saved = expenseRepository.save(expense);
        activity.expenseAdded(callerId, saved);
        return ExpenseResponse.from(saved);
    }

    /**
     * Replaces the expense's details and shares. The group cannot change. {@code version} must be the version
     * the client last read; otherwise someone else edited it in between and the update is refused (409).
     */
    @Transactional
    public ExpenseResponse updateExpense(Long callerId, Long expenseId, UpdateExpenseRequest request) {
        Set<Long> newlyInvolved = request.shares().stream().map(ShareRequest::userId).collect(Collectors.toSet());
        newlyInvolved.add(request.paidById());
        Expense expense = requireAccessibleForWrite(callerId, expenseId, newlyInvolved);
        if (expense.isDeleted()) {
            throw new ConflictException("Expense " + expenseId + " is deleted; restore it before editing");
        }
        if (expense.getVersion() != request.version()) {
            throw new ConflictException("Expense " + expenseId + " was changed by someone else (now version "
                    + expense.getVersion() + "); reload it and try again");
        }
        // People who have left may stay in an expense they were already part of, as long as their balance stays
        ValidatedSplit split = validate(callerId, expense.getGroup(), request.paidById(), request.currency(),
                request.amount(), request.splitType(), request.shares(), expense.involvedUserIds());
        BalanceEffect.requireFormerMembersUnchanged(expense.getGroup(), BalanceEffect.of(expense),
                BalanceEffect.of(split.payer(), request.amount(), split.shares()), "change who pays or owes");
        ExpenseSnapshot before = ExpenseSnapshot.of(expense);

        expense.update(request.description().trim(), request.amount(), split.currency(), split.payer(),
                dateOrToday(request.date()), request.splitType(), split.shares(), split.splitValues(),
                userService.getById(callerId), Instant.now(clock));
        expense.describe(request.category(), request.notes());
        // Flush so the new version is in the response and a lost race fails here, inside the transaction
        Expense saved = expenseRepository.saveAndFlush(expense);
        activity.expenseUpdated(callerId, saved, before);
        return ExpenseResponse.from(saved);
    }

    /** Soft delete: the expense leaves lists and balances but can be restored. Deleting twice is a no-op. */
    @Transactional
    public void deleteExpense(Long callerId, Long expenseId) {
        Expense expense = requireAccessibleForWrite(callerId, expenseId, Set.of());
        if (!expense.isDeleted()) {
            BalanceEffect.requireFormerMembersUnchanged(expense.getGroup(), BalanceEffect.of(expense),
                    BalanceEffect.NONE, "delete this expense");
            expense.markDeleted(userService.getById(callerId), Instant.now(clock));
            activity.expenseDeleted(callerId, expense);
        }
    }

    /**
     * Brings a deleted expense back into the balances, unless that would give someone who has left the group a
     * balance there.
     */
    @Transactional
    public ExpenseResponse restoreExpense(Long callerId, Long expenseId) {
        Expense expense = requireAccessibleForWrite(callerId, expenseId, Set.of());
        if (!expense.isDeleted()) {
            throw new ConflictException("Expense " + expenseId + " is not deleted");
        }
        BalanceEffect.requireFormerMembersUnchanged(expense.getGroup(), BalanceEffect.NONE, BalanceEffect.of(expense),
                "restore this expense");
        expense.restore();
        activity.expenseRestored(callerId, expense);
        return ExpenseResponse.from(expenseRepository.saveAndFlush(expense));
    }

    /**
     * Runs the full create-time validation (currency, split arithmetic, users, group membership) without saving,
     * so a recurring template is only accepted if its expenses could be created.
     */
    public void checkDraft(Long callerId, Group group, Long paidById, String currency, BigDecimal amount,
                           SplitType splitType, List<ShareRequest> shares) {
        validate(callerId, group, paidById, currency, amount, splitType, shares, Set.of());
    }

    /**
     * Creates the expense for one occurrence of a recurring template, in the name of the template's creator.
     * The caller (the scheduler) has locked the group and checked that everyone involved is still a member.
     */
    @Transactional
    public Expense addRecurringOccurrence(RecurringExpense template, LocalDate occurrence) {
        List<ShareRequest> shareRequests = template.getShares().entrySet().stream()
                .map(e -> new ShareRequest(e.getKey(), e.getValue()))
                .toList();
        Long creatorId = template.getCreatedBy().getId();
        ValidatedSplit split = validate(creatorId, template.getGroup(), template.getPaidBy().getId(),
                template.getCurrency(), template.getAmount(), template.getSplitType(), shareRequests, Set.of());
        Expense expense = new Expense(template.getDescription(), template.getAmount(), split.currency(),
                split.payer(), template.getGroup(), occurrence, template.getSplitType(), template.getCreatedBy());
        expense.describe(template.getCategory(), template.getNotes());
        expense.linkToRecurring(template, occurrence);
        split.addSharesTo(expense);
        Expense saved = expenseRepository.save(expense);
        activity.expenseAdded(creatorId, saved);
        return saved;
    }

    /** A group's expenses (not deleted), newest first, filtered and paged. */
    public PageResponse<ExpenseResponse> listForGroup(Long callerId, Long groupId, ExpenseFilter filter, int page,
                                                      int size) {
        groupService.requireMemberGroup(groupId, callerId);
        Specification<Expense> spec = ExpenseSpecs.notDeleted().and(ExpenseSpecs.inGroup(groupId))
                .and(ExpenseSpecs.matching(filter));
        return PageResponse.of(expenseRepository.findAll(spec, Paging.of(page, size, NEWEST_FIRST)),
                ExpenseResponse::from);
    }

    /**
     * Every expense the user paid for or shares in, across groups, newest first, filtered and paged; with
     * {@code withUserId}, only those that person is in too (a friend's page).
     */
    public PageResponse<ExpenseResponse> listForUser(Long callerId, ExpenseFilter filter, Long withUserId, int page,
                                                     int size) {
        Specification<Expense> spec = ExpenseSpecs.notDeleted().and(ExpenseSpecs.involving(callerId))
                .and(ExpenseSpecs.matching(filter));
        if (withUserId != null) {
            spec = spec.and(ExpenseSpecs.involving(withUserId));
        }
        return PageResponse.of(expenseRepository.findAll(spec, Paging.of(page, size, NEWEST_FIRST)),
                ExpenseResponse::from);
    }

    /** Also returns deleted expenses (flagged), so they can be shown and restored. */
    public ExpenseResponse get(Long callerId, Long expenseId) {
        Expense expense = findExpense(expenseId);
        requireAccess(expense, callerId);
        return ExpenseResponse.from(expense);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * @param shares      owed amount per user
     * @param splitValues what was entered per user (null for EQUAL, and for an adjustment left empty)
     */
    private record ValidatedSplit(String currency, User payer, Map<User, BigDecimal> shares,
                                  Map<Long, BigDecimal> splitValues) {

        void addSharesTo(Expense expense) {
            shares.forEach((user, amount) -> expense.addShare(user, amount, splitValues.get(user.getId())));
        }
    }

    /**
     * Checks currency, split arithmetic and the users involved, in that order (400 before 404), and returns the
     * resolved payer and the owed amount per user. Shared by create and edit, so both apply identical rules.
     *
     * @param formerMembersAllowed people who may be in the split without being members now (on an edit, those
     *                             already in the expense; the caller then checks their balance does not move)
     */
    private ValidatedSplit validate(Long callerId, Group group, Long paidById, String requestedCurrency,
                                    BigDecimal amount, SplitType splitType, List<ShareRequest> shareRequests,
                                    Set<Long> formerMembersAllowed) {
        Set<Long> involved = shareRequests.stream().map(ShareRequest::userId).collect(Collectors.toSet());
        involved.add(paidById);
        if (group == null && !involved.contains(callerId)) {
            throw new ForbiddenException("You can only record non-group expenses that you paid for or share in");
        }

        String currency = CurrencyRules.resolve(requestedCurrency, group);
        List<SplitParticipant> participants = shareRequests.stream()
                .map(s -> new SplitParticipant(s.userId(), s.value()))
                .toList();
        Map<Long, BigDecimal> owed = splitStrategies.forType(splitType).split(amount, participants);

        // After the input checks (400), before loading users: strangers and unknown ids get the same 403
        if (group == null) {
            contacts.requireContacts(callerId, involved);
        }
        // Non-group: lock everyone involved (the lock a friend settle-up takes); an edit already holds these
        Map<Long, User> users = group == null ? userService.lockAll(involved) : userService.getAllById(involved);
        if (group != null) {
            for (Long userId : involved) {
                if (!group.hasMember(userId) && !formerMembersAllowed.contains(userId)) {
                    throw new BadRequestException(NOT_ALL_MEMBERS);
                }
            }
        }
        Map<User, BigDecimal> shares = new LinkedHashMap<>();
        owed.forEach((userId, value) -> shares.put(users.get(userId), value));
        Map<Long, BigDecimal> splitValues = new HashMap<>();
        if (splitType != SplitType.EQUAL) {
            shareRequests.forEach(s -> splitValues.put(s.userId(), s.value()));
        }
        return new ValidatedSplit(currency, users.get(paidById), shares, splitValues);
    }

    private Expense findExpense(Long expenseId) {
        return expenseRepository.findDetailedById(expenseId)
                .orElseThrow(() -> new NotFoundException("Expense " + expenseId + " not found"));
    }

    /**
     * Loads the expense for a write, locking first and reading after. For a group expense the group row is locked,
     * so the membership checks below and any membership change cannot interleave. For a non-group expense the
     * people in it and {@code alsoInvolved} (those an edit brings in) are locked together, in one id order.
     */
    private Expense requireAccessibleForWrite(Long callerId, Long expenseId, Set<Long> alsoInvolved) {
        Long groupId = expenseRepository.findGroupIdById(expenseId).orElse(null);
        if (groupId != null) {
            groupService.lockMemberGroup(groupId, callerId);
        } else {
            Set<Long> involved = new HashSet<>(expenseRepository.findNonGroupUserIdsById(expenseId));
            if (!involved.isEmpty()) {
                involved.addAll(alsoInvolved);
                userService.lockExisting(involved);
            }
        }
        Expense expense = findExpense(expenseId);
        requireAccess(expense, callerId);
        return expense;
    }

    private void requireAccess(Expense expense, Long callerId) {
        if (expense.getGroup() != null) {
            groupService.requireMemberGroup(expense.getGroup().getId(), callerId);
        } else if (!expense.involvedUserIds().contains(callerId)) {
            throw new ForbiddenException("You are not part of this expense");
        }
    }

    private LocalDate dateOrToday(LocalDate date) {
        return date != null ? date : today.get();
    }
}
