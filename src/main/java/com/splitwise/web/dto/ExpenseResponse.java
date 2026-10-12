package com.splitwise.web.dto;

import com.splitwise.domain.Category;
import com.splitwise.domain.Expense;
import com.splitwise.domain.SplitType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param version   send it back when editing (optimistic locking)
 * @param deleted   true for a soft-deleted expense (only returned by GET /api/expenses/{id})
 * @param recurringExpenseId the recurring template this expense was created from, if any
 */
public record ExpenseResponse(Long id, String description, BigDecimal amount, String currency,
                              UserSummary paidBy, @Schema(nullable = true) Long groupId, LocalDate date, SplitType splitType,
                              Category category, @Schema(nullable = true) String notes, List<ShareResponse> shares, long version, Long createdById, Instant createdAt,
                              @Schema(nullable = true) Long updatedById, @Schema(nullable = true) Instant updatedAt, boolean deleted, @Schema(nullable = true) Long recurringExpenseId) {

    public record UserSummary(Long id, String name) {
    }

    /** 2.5000 -> 2.5 and 100.0000 -> 100 (not 1E+2). */
    private static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public static ExpenseResponse from(Expense e) {
        List<ShareResponse> shares = e.getShares().stream()
                .map(s -> new ShareResponse(s.getUser().getId(), s.getUser().getName(), s.getAmount(),
                        plain(s.getSplitValue())))
                .toList();
        // Only ids for createdBy / updatedBy: reading the id of a lazy reference does not load the user
        return new ExpenseResponse(e.getId(), e.getDescription(), e.getAmount(), e.getCurrency(),
                new UserSummary(e.getPaidBy().getId(), e.getPaidBy().getName()),
                e.getGroup() == null ? null : e.getGroup().getId(),
                e.getExpenseDate(), e.getSplitType(), e.getCategory(), e.getNotes(), shares, e.getVersion(),
                e.getCreatedBy().getId(), e.getCreatedAt(),
                e.getUpdatedBy() == null ? null : e.getUpdatedBy().getId(), e.getUpdatedAt(), e.isDeleted(),
                e.getRecurringExpense() == null ? null : e.getRecurringExpense().getId());
    }
}
