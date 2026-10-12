package com.splitwise.service;

import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.User;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.web.dto.CreateSettlementRequest;
import com.splitwise.web.dto.SettlementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public SettlementService(SettlementRepository settlementRepository, GroupService groupService,
                             UserService userService) {
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
        this.userService = userService;
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
            group = groupService.requireMemberGroup(request.groupId(), callerId);
            for (Long userId : List.of(request.fromUserId(), request.toUserId())) {
                if (!group.hasMember(userId)) {
                    throw new BadRequestException("User " + userId + " is not a member of group " + group.getId());
                }
            }
        }
        Map<Long, User> users = userService.getAllById(Set.of(request.fromUserId(), request.toUserId()));
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();
        Settlement settlement = new Settlement(users.get(request.fromUserId()), users.get(request.toUserId()),
                request.amount(), Money.DEFAULT_CURRENCY, group, date,
                request.note() == null ? null : request.note().trim(), users.get(callerId));
        return SettlementResponse.from(settlementRepository.save(settlement));
    }

    public List<SettlementResponse> listForGroup(Long callerId, Long groupId) {
        groupService.requireMemberGroup(groupId, callerId);
        return settlementRepository.findByGroupIdOrderBySettledOnDescIdDesc(groupId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    public List<SettlementResponse> listForUser(Long callerId) {
        return settlementRepository.findInvolvingUser(callerId).stream().map(SettlementResponse::from).toList();
    }
}
