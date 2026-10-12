package com.splitwise.service;

import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
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

    private final SettlementRepository settlementRepository;
    private final GroupService groupService;
    private final UserService userService;
    private final Clock clock;

    public SettlementService(SettlementRepository settlementRepository, GroupService groupService,
                             UserService userService, Clock clock) {
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional
    public SettlementResponse create(Long callerId, CreateSettlementRequest request) {
        if (request.fromUserId().equals(request.toUserId())) {
            throw new BadRequestException("A settlement needs two different users");
        }
        if (!callerId.equals(request.fromUserId()) && !callerId.equals(request.toUserId())) {
            throw new ForbiddenException("You can only record settlements you paid or received");
        }
        Group group = null;
        if (request.groupId() != null) {
            group = groupService.lockMemberGroup(request.groupId(), callerId);
            for (Long userId : List.of(request.fromUserId(), request.toUserId())) {
                if (!group.hasMember(userId)) {
                    throw new BadRequestException("User " + userId + " is not a member of group " + group.getId());
                }
            }
        }
        Map<Long, User> users = userService.getAllById(Set.of(request.fromUserId(), request.toUserId()));
        LocalDate date = request.date() != null ? request.date() : LocalDate.now(clock);
        Settlement settlement = new Settlement(users.get(request.fromUserId()), users.get(request.toUserId()),
                request.amount(), Money.DEFAULT_CURRENCY, group, date,
                request.note() == null ? null : request.note().trim(), users.get(callerId));
        return SettlementResponse.from(settlementRepository.save(settlement));
    }

    public List<SettlementResponse> listForGroup(Long callerId, Long groupId) {
        groupService.requireMemberGroup(groupId, callerId);
        return settlementRepository.findByGroupIdAndDeletedAtIsNullOrderBySettledOnDescIdDesc(groupId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    public List<SettlementResponse> listForUser(Long callerId) {
        return settlementRepository.findInvolvingUser(callerId).stream().map(SettlementResponse::from).toList();
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
            settlement.markDeleted(userService.getById(callerId), Instant.now(clock));
        }
    }

    /** Restores a deleted settlement; both people must still be in the group. */
    @Transactional
    public SettlementResponse restore(Long callerId, Long settlementId) {
        Settlement settlement = requireAccessibleForWrite(callerId, settlementId);
        if (!settlement.isDeleted()) {
            throw new ConflictException("Settlement " + settlementId + " is not deleted");
        }
        Group group = settlement.getGroup();
        if (group != null) {
            for (Long userId : List.of(settlement.getPayer().getId(), settlement.getPayee().getId())) {
                if (!group.hasMember(userId)) {
                    throw new ConflictException("Cannot restore: user " + userId + " is no longer a member of group "
                            + group.getId());
                }
            }
        }
        settlement.restore();
        return SettlementResponse.from(settlementRepository.saveAndFlush(settlement));
    }

    private Settlement findSettlement(Long settlementId) {
        return settlementRepository.findDetailedById(settlementId)
                .orElseThrow(() -> new NotFoundException("Settlement " + settlementId + " not found"));
    }

    /** Group settlements lock the group row first, like expenses. */
    private Settlement requireAccessibleForWrite(Long callerId, Long settlementId) {
        Long groupId = settlementRepository.findGroupIdById(settlementId).orElse(null);
        if (groupId != null) {
            groupService.lockMemberGroup(groupId, callerId);
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
