package com.splitwise.activity;

import com.splitwise.activity.ExpenseSnapshot.Share;
import com.splitwise.domain.Category;
import com.splitwise.domain.SplitType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseDiffTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    private static ExpenseSnapshot dinner(String amount, Map<Long, Share> shares) {
        return new ExpenseSnapshot("Dinner", new BigDecimal(amount), "INR", 1L, "Alice", DAY, SplitType.EQUAL,
                Category.GENERAL, null, new TreeMap<>(shares));
    }

    private static Share share(String name, String amount) {
        return new Share(name, new BigDecimal(amount));
    }

    @Test
    void noChangesGiveAnEmptyList() {
        ExpenseSnapshot s = dinner("300.00", Map.of(1L, share("Alice", "150.00"), 2L, share("Bob", "150.00")));

        assertThat(ExpenseDiff.changes(s, s)).isEmpty();
    }

    @Test
    void everyFieldChangeIsDescribedInAFixedOrder() {
        ExpenseSnapshot before = dinner("300.00", Map.of(1L, share("Alice", "300.00")));
        ExpenseSnapshot after = new ExpenseSnapshot("Team dinner", new BigDecimal("400.5"), "INR", 2L, "Bob",
                DAY.plusDays(1), SplitType.EXACT, Category.FOOD_AND_DRINK, "with receipts",
                new TreeMap<>(Map.of(1L, share("Alice", "400.50"))));

        assertThat(ExpenseDiff.changes(before, after)).containsExactly(
                "Description: \"Dinner\" -> \"Team dinner\"",
                "Amount: 300.00 -> 400.50",
                "Paid by: Alice -> Bob",
                "Date: 2026-10-01 -> 2026-10-02",
                "Split: EQUAL -> EXACT",
                "Category: GENERAL -> FOOD_AND_DRINK",
                "Notes added",
                "Alice's share: 300.00 -> 400.50");
    }

    @Test
    void sharesAddedRemovedAndChangedAreListedPerPerson() {
        ExpenseSnapshot before = dinner("300.00", Map.of(
                1L, share("Alice", "100.00"), 2L, share("Bob", "100.00"), 3L, share("Carol", "100.00")));
        ExpenseSnapshot after = dinner("300.00", Map.of(
                1L, share("Alice", "100.00"), 2L, share("Bob", "150.00"), 4L, share("Dan", "50.00")));

        assertThat(ExpenseDiff.changes(before, after)).containsExactly(
                "Bob's share: 100.00 -> 150.00",
                "Carol removed from the split (was 100.00)",
                "Dan added to the split (50.00)");
    }

    @Test
    void amountsThatDifferOnlyInScaleAreNotAChange() {
        ExpenseSnapshot before = dinner("300.00", Map.of(1L, share("Alice", "300.00")));
        ExpenseSnapshot after = dinner("300", Map.of(1L, share("Alice", "300")));

        assertThat(ExpenseDiff.changes(before, after)).isEmpty();
    }

    @Test
    void notesRemovedAndChangedAreReportedWithoutTheirText() {
        ExpenseSnapshot withNotes = new ExpenseSnapshot("Dinner", BigDecimal.TEN, "INR", 1L, "Alice", DAY,
                SplitType.EQUAL, Category.GENERAL, "old", Map.of());
        ExpenseSnapshot otherNotes = new ExpenseSnapshot("Dinner", BigDecimal.TEN, "INR", 1L, "Alice", DAY,
                SplitType.EQUAL, Category.GENERAL, "new", Map.of());
        ExpenseSnapshot noNotes = new ExpenseSnapshot("Dinner", BigDecimal.TEN, "INR", 1L, "Alice", DAY,
                SplitType.EQUAL, Category.GENERAL, null, Map.of());

        assertThat(ExpenseDiff.changes(withNotes, otherNotes)).containsExactly("Notes changed");
        assertThat(ExpenseDiff.changes(withNotes, noNotes)).containsExactly("Notes removed");
    }
}
