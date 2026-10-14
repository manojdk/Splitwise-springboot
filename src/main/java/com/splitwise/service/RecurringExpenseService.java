package com.splitwise.service;

import com.splitwise.activity.ActivityRecorder;
import com.splitwise.domain.Category;
import com.splitwise.domain.Group;
import com.splitwise.domain.RecurringExpense;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.exception.NotFoundException;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.RecurringExpenseRepository;
import com.splitwise.web.CallerToday;
import com.splitwise.web.dto.CreateRecurringExpenseRequest;
import com.splitwise.web.dto.RecurringExpenseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Recurring expenses: templates, and turning their due occurrences into real expenses.
 *
 * <p>Generation ({@link #generateDue}) runs one template per transaction. It locks the group row (the same lock
 * expense writes and membership changes take) and then the template row, and the database refuses a second
 * expense for the same (template, date). So an occurrence is created exactly once, even if two scheduler runs
 * or two app instances overlap. Missed runs are caught up (at most {@link #MAX_CATCH_UP} per run).
 *
 * <p>If someone in the template is no longer in the group, the template is paused instead of creating a
 * half-valid expense, and the pause appears in the activity feed.
 */
@Service
@Transactional(readOnly = true)
public class RecurringExpenseService {

    static final int MAX_CATCH_UP = 60;
    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);

    private final RecurringExpenseRepository recurringRepository;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final UserService userService;
    private final ActivityRecorder activity;
    private final Clock clock;
    private final CallerToday callerToday;

    public RecurringExpenseService(RecurringExpenseRepository recurringRepository, GroupRepository groupRepository,
                                   GroupService groupService, ExpenseService expenseService, UserService userService,
                                   ActivityRecorder activity, Clock clock, CallerToday callerToday) {
        this.recurringRepository = recurringRepository;
        this.groupRepository = groupRepository;
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.userService = userService;
        this.activity = activity;
        this.clock = clock;
        this.callerToday = callerToday;
    }

    /**
     * Saves a template after checking that its expense would be valid today. If it starts today, the first
     * expense is created straight away.
     */
    @Transactional
    public RecurringExpenseResponse create(Long callerId, CreateRecurringExpenseRequest request) {
        Group group = request.groupId() == null ? null : groupService.lockMemberGroup(request.groupId(), callerId);
        // The caller's today, so a start date of "today" is accepted wherever they are; the job runs on server time
        LocalDate today = callerToday.get();
        if (request.startDate().isBefore(today)) {
            throw new BadRequestException("startDate cannot be in the past");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("endDate cannot be before startDate");
        }
        expenseService.checkDraft(callerId, group, request.paidById(), request.currency(), request.amount(),
                request.splitType(), request.shares());

        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        request.shares().forEach(s -> shares.put(s.userId(), s.value()));
        RecurringExpense template = new RecurringExpense(group, request.description().trim(), request.amount(),
                CurrencyRules.resolve(request.currency(), group), userService.getById(request.paidById()),
                request.splitType(), request.category() == null ? Category.GENERAL : request.category(),
                blankToNull(request.notes()), shares, request.frequency(), request.startDate(), request.endDate(),
                userService.getById(callerId), Instant.now(clock));
        RecurringExpense saved = recurringRepository.save(template);
        activity.recurringCreated(callerId, saved);
        createDueOccurrences(saved, group, today);
        return RecurringExpenseResponse.from(saved);
    }

    public RecurringExpenseResponse get(Long callerId, Long id) {
        RecurringExpense template = find(id);
        requireAccess(template, callerId);
        return RecurringExpenseResponse.from(template);
    }

    /** Templates in the user's groups and non-group templates they are part of, newest first. */
    public List<RecurringExpenseResponse> listForUser(Long callerId) {
        List<RecurringExpense> all = new ArrayList<>();
        List<Long> groupIds = groupRepository.findIdsForMember(callerId);
        if (!groupIds.isEmpty()) {
            all.addAll(recurringRepository.findByGroupIdInOrderByIdDesc(groupIds));
        }
        all.addAll(recurringRepository.findNonGroupForUser(callerId));
        return all.stream()
                .sorted(Comparator.comparing(RecurringExpense::getId).reversed())
                .map(RecurringExpenseResponse::from)
                .toList();
    }

    /** Stops future occurrences; expenses already created stay. Stopping twice is a no-op. */
    @Transactional
    public void stop(Long callerId, Long id) {
        Long groupId = recurringRepository.findGroupIdById(id).orElse(null);
        if (groupId != null) {
            groupService.lockMemberGroup(groupId, callerId);
        }
        RecurringExpense template = recurringRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Recurring expense " + id + " not found"));
        requireAccess(template, callerId);
        if (template.isActive()) {
            template.finish("Stopped by " + userService.getById(callerId).getName());
            activity.recurringStopped(callerId, template);
        }
    }

    /** Ids of templates with an occurrence due today or earlier. */
    public List<Long> findDueIds() {
        return recurringRepository.findDueIds(LocalDate.now(clock));
    }

    /**
     * Creates every due occurrence of one template, in one transaction.
     *
     * @return the number of expenses created
     */
    @Transactional
    public int generateDue(Long templateId) {
        Long groupId = recurringRepository.findGroupIdById(templateId).orElse(null);
        // Lock order everywhere: group, then template. No membership check on the lock itself: the creator may
        // have left, which is handled below by pausing.
        Group group = groupId == null ? null : groupRepository.findByIdForUpdate(groupId).orElse(null);
        RecurringExpense template = recurringRepository.findByIdForUpdate(templateId).orElse(null);
        if (template == null) {
            return 0;
        }
        return createDueOccurrences(template, group, LocalDate.now(clock));
    }

    private int createDueOccurrences(RecurringExpense template, Group group, LocalDate today) {
        int created = 0;
        while (template.isDue(today) && created < MAX_CATCH_UP) {
            String missing = missingMember(template, group);
            if (missing != null) {
                String reason = missing + " is no longer a member of the group";
                template.finish("Paused: " + reason);
                activity.recurringPaused(template, reason);
                log.info("Recurring expense {} paused: {}", template.getId(), reason);
                break;
            }
            expenseService.addRecurringOccurrence(template, template.nextDate());
            template.advance();
            created++;
        }
        return created;
    }

    /** Name of someone in the template (or its creator) who has left the group, or null if all are members. */
    private String missingMember(RecurringExpense template, Group group) {
        if (group == null) {
            return null;
        }
        Set<Long> needed = new TreeSet<>(template.getShares().keySet());
        needed.add(template.getPaidBy().getId());
        needed.add(template.getCreatedBy().getId());
        for (Long userId : needed) {
            if (!group.hasMember(userId)) {
                return userService.getById(userId).getName();
            }
        }
        return null;
    }

    private RecurringExpense find(Long id) {
        return recurringRepository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Recurring expense " + id + " not found"));
    }

    /** Group template: members. Non-group: the people in it, the payer and the creator. */
    private void requireAccess(RecurringExpense template, Long callerId) {
        if (template.getGroup() != null) {
            groupService.requireMemberGroup(template.getGroup().getId(), callerId);
            return;
        }
        boolean involved = template.getShares().containsKey(callerId)
                || template.getPaidBy().getId().equals(callerId)
                || template.getCreatedBy().getId().equals(callerId);
        if (!involved) {
            throw new ForbiddenException("You are not part of this recurring expense");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
