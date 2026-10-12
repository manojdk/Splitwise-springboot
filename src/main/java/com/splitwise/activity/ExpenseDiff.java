package com.splitwise.activity;

import com.splitwise.activity.ExpenseSnapshot.Share;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Describes what changed between two versions of an expense, one human-readable line per change, for the
 * activity feed. Pure function, no Spring.
 */
public final class ExpenseDiff {

    private ExpenseDiff() {
    }

    public static List<String> changes(ExpenseSnapshot before, ExpenseSnapshot after) {
        List<String> changes = new ArrayList<>();
        if (!before.description().equals(after.description())) {
            changes.add("Description: \"" + before.description() + "\" -> \"" + after.description() + "\"");
        }
        if (before.amount().compareTo(after.amount()) != 0) {
            changes.add("Amount: " + money(before.amount()) + " -> " + money(after.amount()));
        }
        if (before.paidById() != after.paidById()) {
            changes.add("Paid by: " + before.paidByName() + " -> " + after.paidByName());
        }
        if (!before.date().equals(after.date())) {
            changes.add("Date: " + before.date() + " -> " + after.date());
        }
        if (before.splitType() != after.splitType()) {
            changes.add("Split: " + before.splitType() + " -> " + after.splitType());
        }
        if (before.category() != after.category()) {
            changes.add("Category: " + before.category() + " -> " + after.category());
        }
        if (!Objects.equals(before.notes(), after.notes())) {
            changes.add(before.notes() == null ? "Notes added"
                    : after.notes() == null ? "Notes removed" : "Notes changed");
        }
        TreeSet<Long> users = new TreeSet<>(before.shares().keySet());
        users.addAll(after.shares().keySet());
        for (Long userId : users) {
            Share old = before.shares().get(userId);
            Share now = after.shares().get(userId);
            if (old == null) {
                changes.add(now.name() + " added to the split (" + money(now.amount()) + ")");
            } else if (now == null) {
                changes.add(old.name() + " removed from the split (was " + money(old.amount()) + ")");
            } else if (old.amount().compareTo(now.amount()) != 0) {
                changes.add(now.name() + "'s share: " + money(old.amount()) + " -> " + money(now.amount()));
            }
        }
        return changes;
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }
}
