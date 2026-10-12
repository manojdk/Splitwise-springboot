package com.splitwise.report;

import com.splitwise.domain.Category;
import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * One expense as reports see it, copied out of the entity so the report and CSV code are pure functions.
 *
 * @param shares owed amount per user id
 */
public record ReportLine(LocalDate date, String description, Category category, BigDecimal amount, String currency,
                         long payerId, String splitType, Map<Long, BigDecimal> shares) {

    public static ReportLine of(Expense e) {
        Map<Long, BigDecimal> shares = new TreeMap<>();
        for (ExpenseShare s : e.getShares()) {
            shares.put(s.getUser().getId(), s.getAmount());
        }
        return new ReportLine(e.getExpenseDate(), e.getDescription(), e.getCategory(), e.getAmount(), e.getCurrency(),
                e.getPaidBy().getId(), e.getSplitType().name(), shares);
    }
}
