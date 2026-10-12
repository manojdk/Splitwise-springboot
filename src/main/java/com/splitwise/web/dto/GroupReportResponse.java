package com.splitwise.web.dto;

import com.splitwise.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Spending in a group over a date range (both ends inclusive; null = open).
 */
public record GroupReportResponse(Long groupId, String groupName, LocalDate from, LocalDate to, String currency,
                                  int expenseCount, BigDecimal totalSpent, List<CategoryTotal> byCategory,
                                  List<MemberTotal> byMember) {

    public record CategoryTotal(Category category, BigDecimal total, int count) {
    }

    /**
     * @param paid  what the member paid for
     * @param share their own share of the expenses
     * @param net   paid - share (positive = paid for others)
     */
    public record MemberTotal(Long userId, String name, BigDecimal paid, BigDecimal share, BigDecimal net) {
    }
}
