package com.splitwise.web.dto;

import com.splitwise.domain.Expense;
import com.splitwise.domain.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseResponse(Long id, String description, BigDecimal amount, String currency,
                              UserSummary paidBy, Long groupId, LocalDate date, SplitType splitType,
                              List<ShareResponse> shares) {

    public record UserSummary(Long id, String name) {
    }

    public static ExpenseResponse from(Expense e) {
        List<ShareResponse> shares = e.getShares().stream()
                .map(s -> new ShareResponse(s.getUser().getId(), s.getUser().getName(), s.getAmount()))
                .toList();
        return new ExpenseResponse(e.getId(), e.getDescription(), e.getAmount(), e.getCurrency(),
                new UserSummary(e.getPaidBy().getId(), e.getPaidBy().getName()),
                e.getGroup() == null ? null : e.getGroup().getId(),
                e.getExpenseDate(), e.getSplitType(), shares);
    }
}
