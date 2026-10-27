package com.splitwise.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import com.splitwise.web.dto.PageResponse;
import com.splitwise.query.SettlementSpecs;
import com.splitwise.query.Paging;
import com.splitwise.activity.ActivityRecorder;
import com.splitwise.balance.BalanceEffect;
import com.splitwise.web.CallerToday;
import com.splitwise.domain.Group;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.User;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ConflictException;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.exception.NotFoundException;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.web.dto.CreateSettlementRequest;
import com.splitwise.web.dto.SettlementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SettlementService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "settledOn", "id");

    private final SettlementRepository settlementRepository;
    private final GroupService groupService;
    private final UserService userService;
    private final Clock clock;
    private final ActivityRecorder activity;
    private final ContactService contacts;
    private final CallerToday today;

    public SettlementService(SettlementRepository settlementRepository, GroupService groupService,
                             UserService userService, Clock clock, ActivityRecorder activity,
                             ContactService contacts, CallerToday today) {
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.clock = clock;
        this.activity = activity;
        this.contacts = contacts;
        this.today = today;
    }

    @Transactional
    public SettlementResponse create(Long callerId, CreateSettlementRequest request) {
        return create(callerId, request, true);
    }

    /**
     * For a friend settle-up, which pays off balances exactly as they are: a yen balance from before amounts had
     * to be whole (100.50) is paid as it is, so it can still be cleared.
     */
    @Transactional
    SettlementResponse createSettleUp(Long callerId, CreateSettlementRequest request) {
        return create(callerId, request, false);
    }

    private SettlementResponse create(Long callerId, CreateSettlementRequest request, boolean amountMustFit) {
        if (request.fromUserId().equals(request.toUserId())) {
            throw new BadRequestException("A settlement needs two different users");
        }
        if (!callerId.equals(request.fromUserId()) && !callerId.equals(request.toUserId())) {
            throw new ForbiddenException("You can only record settlements you paid or received");
        }
        if (request.groupId() == null) {
            contacts.requireContacts(callerId, List.of(request.fromUserId(), request.toUserId()));
        }
        Group group = null;
        if (request.groupId() != null) {
            group = groupService.lockMemberGroup(request.groupId(), callerId);
            for (Long userId : List.of(request.fromUserId(), request.toUserId())) {
                if (!group.hasMember(userId)) {
                    throw new BadRequestException(ExpenseService.NOT_ALL_MEMBERS);
                }
            }
        }
        // Non-group: lock both users, the same lock a friend settle-up takes, so the two cannot race
        Map<Long, User> users = group == null
                ? userService.lockAll(Set.of(request.fromUserId(), request.toUserId()))
                : userService.getAllById(Set.of(request.fromUserId(), request.toUserId()));
        String currency = CurrencyRules.resolve(request.currency(), group);
        if (amountMustFit) {
            CurrencyRules.requireFits(request.amount(), currency);
        }
        LocalDate date = request.date() != null ? request.date() : today.get();
        Settlement settlement = new Settlement(users.get(request.fromUserId()), users.get(request.toUserId()),
                request.amount(), currency, group, date,
                request.note() == null ? null : request.note().trim(), users.get(callerId));
        Settlement saved = settlementRepository.save(settlement);
        activity.settlementAdded(callerId, saved);
        return SettlementResponse.from(saved);
    }

    /** A group's settlements (not deleted), newest first, optionally within dates, paged. */
    public PageResponse<SettlementResponse> listForGroup(Long callerId, Long groupId, LocalDate from, LocalDate to,
                                                         int page, int size) {
        groupService.requireMemberGroup(groupId, callerId);
        Specification<Settlement> spec = SettlementSpecs.notDeleted().and(SettlementSpecs.inGroup(groupId))
                .and(SettlementSpecs.between(from, to));
        return PageResponse.of(settlementRepository.findAll(spec, Paging.of(page, size, NEWEST_FIRST)),
                SettlementResponse::from);
    }

    /** Settlements the user paid or received, newest first, optionally within dates, paged. */
    public PageResponse<SettlementResponse> listForUser(Long callerId, LocalDate from, LocalDate to, int page,
                                                        int size) {
        Specification<Settlement> spec = SettlementSpecs.notDeleted().and(SettlementSpecs.involving(callerId))
                .and(SettlementSpecs.between(from, to));
        return PageResponse.of(settlementRepository.findAll(spec, Paging.of(page, size, NEWEST_FIRST)),
                SettlementResponse::from);
    }

    /** Also returns deleted settlements (flagged), so they can be shown and restored. */
    public SettlementResponse get(Long callerId, Long settlementId) {
        Settlement settlement = findSettlement(settlementId);
        requireAccess(settlement, callerId);
        return SettlementResponse.from(settlement);
    }

    /** Soft delete ("undo" a payment recorded by mistake). Deleting twice is a no-op. */
    @Transactional
    public void delete(Long callerId, Long settlementId) {
        Settlement settlement = requireAccessibleForWrite(callerId, settlementId);
        if (!settlement.isDeleted()) {
            BalanceEffect.requireFormerMembersUnchanged(settlement.getGroup(), BalanceEffect.of(settlement),
                    BalanceEffect.NONE, "delete this payment");
            settlement.markDeleted(userService.getById(callerId), Instant.now(clock));
            activity.settlementDeleted(callerId, settlement);
        }
    }

    /** Restores a deleted settlement; both people must still be in the group. */
    @Transactional
    public SettlementResponse restore(Long callerId, Long settlementId) {
        Settlement settlement = requireAccessibleForWrite(callerId, settlementId);
        if (!settlement.isDeleted()) {
            throw new ConflictException("Settlement " + settlementId + " is not deleted");
        }
        BalanceEffect.requireFormerMembersUnchanged(settlement.getGroup(), BalanceEffect.NONE,
                BalanceEffect.of(settlement), "restore this payment");
        settlement.restore();
        activity.settlementRestored(callerId, settlement);
        return SettlementResponse.from(settlementRepository.saveAndFlush(settlement));
    }

    private Settlement findSettlement(Long settlementId) {
        return settlementRepository.findDetailedById(settlementId)
                .orElseThrow(() -> new NotFoundException("Settlement " + settlementId + " not found"));
    }

    /**
     * Lock first, read after, like expenses: a group settlement locks the group row, a non-group one its payer and
     * payee (the lock a friend settle-up takes).
     */
    private Settlement requireAccessibleForWrite(Long callerId, Long settlementId) {
        Long groupId = settlementRepository.findGroupIdById(settlementId).orElse(null);
        if (groupId != null) {
            groupService.lockMemberGroup(groupId, callerId);
        } else {
            userService.lockExisting(settlementRepository.findNonGroupUserIdsById(settlementId));
        }
        Settlement settlement = findSettlement(settlementId);
        requireAccess(settlement, callerId);
        return settlement;
    }

    /** Group settlement: any member of the group. Non-group settlement: the payer or the payee. */
    private void requireAccess(Settlement settlement, Long callerId) {
        if (settlement.getGroup() != null) {
            groupService.requireMemberGroup(settlement.getGroup().getId(), callerId);
        } else if (!settlement.involves(callerId)) {
            throw new ForbiddenException("You are not part of this settlement");
        }
    }
}
