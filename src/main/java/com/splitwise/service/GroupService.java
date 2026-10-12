package com.splitwise.service;

import com.splitwise.domain.Group;
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

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final UserService userService;

    public GroupService(GroupRepository groupRepository, ExpenseRepository expenseRepository,
                        SettlementRepository settlementRepository, UserService userService) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.userService = userService;
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
            throw new ForbiddenException("You are not a member of group " + groupId);
        }
        return group;
    }

    @Transactional
    public GroupResponse create(Long callerId, CreateGroupRequest request) {
        User creator = userService.getById(callerId);
        Group group = new Group(request.name().trim(), creator);
        if (request.memberIds() != null && !request.memberIds().isEmpty()) {
            userService.getAllById(request.memberIds()).values().forEach(group::addMember);
        }
        return GroupResponse.from(groupRepository.save(group));
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
        group.setName(request.name().trim());
        return GroupResponse.from(group);
    }

    @Transactional
    public GroupResponse addMember(Long callerId, Long groupId, AddMemberRequest request) {
        Group group = requireMemberGroup(groupId, callerId);
        User user = userService.getByEmail(request.email());
        if (group.hasMember(user.getId())) {
            throw new ConflictException("User is already a member of this group");
        }
        group.addMember(user);
        return GroupResponse.from(group);
    }

    /** Only the creator may delete, and only while the group has no expenses or settlements. */
    @Transactional
    public void delete(Long callerId, Long groupId) {
        Group group = requireMemberGroup(groupId, callerId);
        if (!group.getCreatedBy().getId().equals(callerId)) {
            throw new ForbiddenException("Only the group creator can delete the group");
        }
        if (expenseRepository.existsByGroupId(groupId) || settlementRepository.existsByGroupId(groupId)) {
            throw new ConflictException("Group has expenses or settlements and cannot be deleted");
        }
        groupRepository.delete(group);
    }
}
