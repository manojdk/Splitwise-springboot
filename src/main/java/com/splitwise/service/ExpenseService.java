package com.splitwise.service;

import com.splitwise.domain.Expense;
import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
import com.splitwise.domain.User;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.exception.NotFoundException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.split.SplitParticipant;
import com.splitwise.split.SplitStrategyResolver;
import com.splitwise.web.dto.CreateExpenseRequest;
import com.splitwise.web.dto.ExpenseResponse;
import com.splitwise.web.dto.ShareRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;
    private final UserService userService;
    private final SplitStrategyResolver splitStrategies;

    public ExpenseService(ExpenseRepository expenseRepository, GroupService groupService,
                          UserService userService, SplitStrategyResolver splitStrategies) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.splitStrategies = splitStrategies;
    }

    /**
     * Validates the request fully (access, membership, split arithmetic) and only then persists the
     * expense and its shares in one transaction.
     */
    @Transactional
    public ExpenseResponse addExpense(Long callerId, CreateExpenseRequest request) {
        // 1. access: group membership (403/404) before anything else
        Group group = request.groupId() == null ? null : groupService.requireMemberGroup(request.groupId(), callerId);

        Set<Long> involved = request.shares().stream().map(ShareRequest::userId).collect(Collectors.toSet());
        involved.add(request.paidById());
        if (group == null && !involved.contains(callerId)) {
            throw new ForbiddenException("You can only record non-group expenses that you paid for or share in");
        }

        // 2. input: currency and split arithmetic (400)
        String currency = resolveCurrency(request.currency());
        List<SplitParticipant> participants = request.shares().stream()
                .map(s -> new SplitParticipant(s.userId(), s.value()))
                .toList();
        Map<Long, BigDecimal> owed = splitStrategies.forType(request.splitType()).split(request.amount(), participants);

        // 3. referenced users must exist (404) and belong to the group (400)
        Map<Long, User> users = userService.getAllById(involved);
        if (group != null) {
            for (Long userId : involved) {
                if (!group.hasMember(userId)) {
                    throw new BadRequestException("User " + userId + " is not a member of group " + group.getId());
                }
            }
        }

        User caller = userService.getById(callerId);
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();

        Expense expense = new Expense(request.description().trim(), request.amount(), currency,
                users.get(request.paidById()), group, date, request.splitType(), caller);
        owed.forEach((userId, amount) -> expense.addShare(users.get(userId), amount));
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    public List<ExpenseResponse> listForGroup(Long callerId, Long groupId) {
        groupService.requireMemberGroup(groupId, callerId);
        return expenseRepository.findByGroupIdOrderByExpenseDateDescIdDesc(groupId).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    public List<ExpenseResponse> listForUser(Long callerId) {
        return expenseRepository.findInvolvingUser(callerId).stream().map(ExpenseResponse::from).toList();
    }

    public ExpenseResponse get(Long callerId, Long expenseId) {
        Expense expense = expenseRepository.findDetailedById(expenseId)
                .orElseThrow(() -> new NotFoundException("Expense " + expenseId + " not found"));
        if (expense.getGroup() != null) {
            groupService.requireMemberGroup(expense.getGroup().getId(), callerId);
        } else if (!isInvolved(expense, callerId)) {
            throw new ForbiddenException("You are not part of this expense");
        }
        return ExpenseResponse.from(expense);
    }

    private static boolean isInvolved(Expense expense, Long userId) {
        List<Long> ids = new ArrayList<>();
        ids.add(expense.getPaidBy().getId());
        expense.getShares().forEach(s -> ids.add(s.getUser().getId()));
        return ids.contains(userId);
    }

    static String resolveCurrency(String requested) {
        if (requested == null || requested.isBlank()) {
            return Money.DEFAULT_CURRENCY;
        }
        if (!Money.DEFAULT_CURRENCY.equals(requested)) {
            throw new BadRequestException("Only " + Money.DEFAULT_CURRENCY + " is supported at the moment");
        }
        return requested;
    }
}
