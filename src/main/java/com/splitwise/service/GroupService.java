package com.splitwise.service;

import com.splitwise.activity.ActivityRecorder;
import com.splitwise.balance.BalanceCalculator;
import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
import com.splitwise.domain.User;
import com.splitwise.exception.ConflictException;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.exception.NotFoundException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.web.dto.AddMemberRequest;
import com.splitwise.web.dto.CreateGroupRequest;
import com.splitwise.web.dto.GroupResponse;
import com.splitwise.web.dto.UpdateGroupRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final UserService userService;
    private final ActivityRecorder activity;

    public GroupService(GroupRepository groupRepository, ExpenseRepository expenseRepository,
                        SettlementRepository settlementRepository, UserService userService,
                        ActivityRecorder activity) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.userService = userService;
        this.activity = activity;
    }

    /**
     * Loads a group the caller belongs to.
     *
     * @throws NotFoundException  if the group does not exist
     * @throws ForbiddenException if the caller is not a member
     */
    public Group requireMemberGroup(Long groupId, Long callerId) {
        Group group = groupRepository.findWithMembersById(groupId)
                .orElseThrow(() -> new NotFoundException("Group " + groupId + " not found"));
        if (!group.hasMember(callerId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
        return group;
    }

    /**
     * Like {@link #requireMemberGroup}, but first locks the group row until the transaction ends. Use it for writes
     * that depend on membership (adding or changing an expense, removing a member), so two such writes on the same
     * group run one after the other. The members are read after the lock, so they are current.
     */
    @Transactional
    public Group lockMemberGroup(Long groupId, Long callerId) {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("Group " + groupId + " not found"));
        if (!group.hasMember(callerId)) {
            throw new ForbiddenException("You are not a member of this group");
        }
        return group;
    }

    @Transactional
    public GroupResponse create(Long callerId, CreateGroupRequest request) {
        User creator = userService.getById(callerId);
        String currency = request.currency() == null ? Money.DEFAULT_CURRENCY
                : CurrencyRules.requireValid(request.currency());
        Group group = new Group(request.name().trim(), creator, currency);
        Group saved = groupRepository.save(group);
        activity.groupCreated(callerId, saved);
        return GroupResponse.from(saved);
    }

    public List<GroupResponse> listForUser(Long callerId) {
        return groupRepository.findAllForMember(callerId).stream().map(GroupResponse::from).toList();
    }

    public GroupResponse get(Long callerId, Long groupId) {
        return GroupResponse.from(requireMemberGroup(groupId, callerId));
    }

    @Transactional
    public GroupResponse rename(Long callerId, Long groupId, UpdateGroupRequest request) {
        Group group = requireMemberGroup(groupId, callerId);
        String oldName = group.getName();
        group.setName(request.name().trim());
        if (!oldName.equals(group.getName())) {
            activity.groupRenamed(callerId, group, oldName);
        }
        return GroupResponse.from(group);
    }

    @Transactional
    public GroupResponse addMember(Long callerId, Long groupId, AddMemberRequest request) {
        Group group = lockMemberGroup(groupId, callerId);
        User user = userService.getByEmail(request.email());
        if (group.hasMember(user.getId())) {
            throw new ConflictException("User is already a member of this group");
        }
        group.addMember(user);
        activity.memberAdded(callerId, group, user);
        return GroupResponse.from(group);
    }

    /**
     * Removes a member, or lets a member leave ({@code memberId} = caller). Rules, as in Splitwise:
     * <ul>
     *   <li>anyone may leave; only the group's creator may remove someone else;</li>
     *   <li>the creator cannot leave (delete the group instead);</li>
     *   <li>the member's balance in this group must be exactly zero, otherwise money would be lost.</li>
     * </ul>
     * The group row is locked, so no expense involving the member can be added while the balance is checked.
     */
    @Transactional
    public GroupResponse removeMember(Long callerId, Long groupId, Long memberId) {
        Group group = lockMemberGroup(groupId, callerId);
        boolean leaving = memberId.equals(callerId);
        if (!leaving && !group.isCreator(callerId)) {
            throw new ForbiddenException("Only the group creator can remove other members");
        }
        if (group.isCreator(memberId)) {
            throw new ConflictException("The group creator cannot leave the group; delete the group instead");
        }
        if (!group.hasMember(memberId)) {
            throw new NotFoundException("That person is not a member of this group");
        }
        BigDecimal balance = BalanceCalculator.netBalances(
                        expenseRepository.findByGroupIdAndDeletedAtIsNullOrderByExpenseDateDescIdDesc(groupId),
                        settlementRepository.findByGroupIdAndDeletedAtIsNullOrderBySettledOnDescIdDesc(groupId))
                .getOrDefault(memberId, Money.ZERO);
        User member = userService.getById(memberId);
        if (balance.signum() != 0) {
            String who = leaving ? "You still have" : member.getName() + " still has";
            throw new ConflictException(who + " a balance of " + group.getCurrency() + " " + balance.toPlainString()
                    + " in this group; settle up first");
        }
        group.removeMember(memberId);
        activity.memberRemoved(callerId, group, member);
        return GroupResponse.from(group);
    }

    /** Only the creator may delete, and only while the group has no expenses or settlements (deleted ones count). */
    @Transactional
    public void delete(Long callerId, Long groupId) {
        Group group = requireMemberGroup(groupId, callerId);
        if (!group.getCreatedBy().getId().equals(callerId)) {
            throw new ForbiddenException("Only the group creator can delete the group");
        }
        if (expenseRepository.existsByGroupId(groupId) || settlementRepository.existsByGroupId(groupId)) {
            throw new ConflictException("Group has expenses or settlements and cannot be deleted");
        }
        activity.groupDeleted(callerId, group);
        groupRepository.delete(group);
    }
}
