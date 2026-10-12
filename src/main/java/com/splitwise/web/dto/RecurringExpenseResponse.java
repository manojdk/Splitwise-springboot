package com.splitwise.web.dto;

import com.splitwise.domain.Category;
import com.splitwise.domain.Frequency;
import com.splitwise.domain.RecurringExpense;
import com.splitwise.domain.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param nextDate      when the next expense will be created; null once the template has ended or stopped
 * @param stoppedReason why it is no longer active (ended, stopped by someone, or paused because someone left)
 */
public record RecurringExpenseResponse(Long id, String description, BigDecimal amount, String currency,
                                       Long paidById, Long groupId, SplitType splitType, Category category,
                                       String notes, List<ShareRequest> shares, Frequency frequency,
                                       LocalDate startDate, LocalDate endDate, LocalDate nextDate, boolean active,
                                       String stoppedReason, Long createdById, Instant createdAt) {

    public static RecurringExpenseResponse from(RecurringExpense r) {
        List<ShareRequest> shares = r.getShares().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> new ShareRequest(e.getKey(), e.getValue() == null ? null : e.getValue().stripTrailingZeros()))
                .toList();
        return new RecurringExpenseResponse(r.getId(), r.getDescription(), r.getAmount(), r.getCurrency(),
                r.getPaidBy().getId(), r.getGroup() == null ? null : r.getGroup().getId(), r.getSplitType(),
                r.getCategory(), r.getNotes(), shares, r.getFrequency(), r.getStartDate(), r.getEndDate(),
                r.isActive() ? r.getNextRunDate() : null, r.isActive(), r.getStoppedReason(),
                r.getCreatedBy().getId(), r.getCreatedAt());
    }
}
