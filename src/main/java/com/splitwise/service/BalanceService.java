package com.splitwise.service;

import com.splitwise.balance.BalanceCalculator;
import com.splitwise.balance.DebtSimplifier;
import com.splitwise.balance.Transfer;
import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
import com.splitwise.domain.User;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.web.dto.BalanceResponse;
import com.splitwise.web.dto.TransferResponse;
import com.splitwise.web.dto.UserBalanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Net balances (positive = is owed, negative = owes) and simplified debts, derived on read from the
 * stored expenses and settlements. Nothing is cached, so balances can never drift out of sync.
 */
@Service
@Transactional(readOnly = true)
public class BalanceService {

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public BalanceService(GroupService groupService, ExpenseRepository expenseRepository,
                          SettlementRepository settlementRepository) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
    }

    /** Every member's net balance within the group (members with nothing outstanding show 0.00). */
    public List<BalanceResponse> groupBalances(Long callerId, Long groupId) {
        Group group = groupService.requireMemberGroup(groupId, callerId);
        Map<Long, BigDecimal> net = netForGroup(group);
        Map<Long, User> members = membersById(group);
        return net.entrySet().stream()
                .map(e -> new BalanceResponse(e.getKey(), members.get(e.getKey()).getName(), e.getValue()))
                .toList();
    }

    /** The minimal-ish list of payments that would settle the group (see {@link DebtSimplifier}). */
    public List<TransferResponse> simplifiedDebts(Long callerId, Long groupId) {
        Group group = groupService.requireMemberGroup(groupId, callerId);
        Map<Long, User> members = membersById(group);
        return DebtSimplifier.simplify(netForGroup(group)).stream()
                .map(t -> toResponse(t, members))
                .toList();
    }

    /** The caller's overall net balance across all groups and non-group expenses. */
    public UserBalanceResponse overallBalance(Long callerId) {
        Map<Long, BigDecimal> net = BalanceCalculator.netBalances(
                expenseRepository.findInvolvingUser(callerId),
                settlementRepository.findInvolvingUser(callerId));
        return new UserBalanceResponse(callerId, net.getOrDefault(callerId, Money.ZERO), Money.DEFAULT_CURRENCY);
    }

    private Map<Long, BigDecimal> netForGroup(Group group) {
        Map<Long, BigDecimal> net = new TreeMap<>();
        group.getMembers().forEach(m -> net.put(m.getId(), Money.ZERO));
        net.putAll(BalanceCalculator.netBalances(
                expenseRepository.findByGroupIdAndDeletedAtIsNullOrderByExpenseDateDescIdDesc(group.getId()),
                settlementRepository.findByGroupIdAndDeletedAtIsNullOrderBySettledOnDescIdDesc(group.getId())));
        // A former member can only have left with a zero balance; keep the list to current members
        net.keySet().retainAll(membersById(group).keySet());
        return net;
    }

    private static Map<Long, User> membersById(Group group) {
        return group.getMembers().stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static TransferResponse toResponse(Transfer t, Map<Long, User> members) {
        return new TransferResponse(t.fromUserId(), members.get(t.fromUserId()).getName(),
                t.toUserId(), members.get(t.toUserId()).getName(), t.amount());
    }
}
