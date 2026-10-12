package com.splitwise.activity;

import com.splitwise.domain.Category;
import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * An expense's visible state at one moment, copied out of the entity so it can be compared after the entity
 * has been changed.
 *
 * @param shares by user id (sorted), each with the user's name and amount
 */
public record ExpenseSnapshot(String description, BigDecimal amount, String currency, long paidById, String paidByName,
                              LocalDate date, SplitType splitType, Category category, String notes,
                              Map<Long, Share> shares) {

    public record Share(String name, BigDecimal amount) {
    }

    public static ExpenseSnapshot of(Expense e) {
        Map<Long, Share> shares = new TreeMap<>();
        for (ExpenseShare s : e.getShares()) {
            shares.put(s.getUser().getId(), new Share(s.getUser().getName(), s.getAmount()));
        }
        return new ExpenseSnapshot(e.getDescription(), e.getAmount(), e.getCurrency(), e.getPaidBy().getId(),
                e.getPaidBy().getName(), e.getExpenseDate(), e.getSplitType(), e.getCategory(), e.getNotes(), shares);
    }
}
