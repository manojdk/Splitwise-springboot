package com.splitwise.web.dto;

import com.splitwise.domain.Expense;
import com.splitwise.domain.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param version   send it back when editing (optimistic locking)
 * @param deleted   true for a soft-deleted expense (only returned by GET /api/expenses/{id})
 */
public record ExpenseResponse(Long id, String description, BigDecimal amount, String currency,
                              UserSummary paidBy, Long groupId, LocalDate date, SplitType splitType,
                              List<ShareResponse> shares, long version, Long createdById, Instant createdAt,
                              Long updatedById, Instant updatedAt, boolean deleted) {

    public record UserSummary(Long id, String name) {
    }

    public static ExpenseResponse from(Expense e) {
        List<ShareResponse> shares = e.getShares().stream()
                .map(s -> new ShareResponse(s.getUser().getId(), s.getUser().getName(), s.getAmount()))
                .toList();
        // Only ids for createdBy / updatedBy: reading the id of a lazy reference does not load the user
        return new ExpenseResponse(e.getId(), e.getDescription(), e.getAmount(), e.getCurrency(),
                new UserSummary(e.getPaidBy().getId(), e.getPaidBy().getName()),
                e.getGroup() == null ? null : e.getGroup().getId(),
                e.getExpenseDate(), e.getSplitType(), shares, e.getVersion(),
                e.getCreatedBy().getId(), e.getCreatedAt(),
                e.getUpdatedBy() == null ? null : e.getUpdatedBy().getId(), e.getUpdatedAt(), e.isDeleted());
    }
}
