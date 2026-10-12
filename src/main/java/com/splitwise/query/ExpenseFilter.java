package com.splitwise.query;

import com.splitwise.domain.Category;
import com.splitwise.exception.BadRequestException;

import java.time.LocalDate;

/**
 * Optional filters for expense lists; null means "no filter".
 *
 * @param from    expense date on or after (inclusive)
 * @param to      expense date on or before (inclusive)
 * @param paidBy  user id of the payer
 * @param q       case-insensitive text to find in the description
 */
public record ExpenseFilter(LocalDate from, LocalDate to, Category category, Long paidBy, String q) {

    public static final int MAX_QUERY_LENGTH = 100;

    public ExpenseFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("q must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        q = q == null || q.isBlank() ? null : q.trim();
    }

    public static ExpenseFilter none() {
        return new ExpenseFilter(null, null, null, null, null);
    }
}
