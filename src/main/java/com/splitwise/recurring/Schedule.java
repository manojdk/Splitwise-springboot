package com.splitwise.recurring;

import com.splitwise.domain.Frequency;

import java.time.LocalDate;

/**
 * When occurrence n of a recurring expense falls. Pure function.
 *
 * <p>Always computed from the start date ({@code start + n periods}), never from the previous occurrence, so
 * month-end dates do not drift: a monthly expense starting on 31 January falls on 28/29 February and on 31 March,
 * not on the 28th forever after. Yearly on 29 February falls on 28 February in non-leap years.
 */
public final class Schedule {

    private Schedule() {
    }

    public static LocalDate occurrence(LocalDate start, Frequency frequency, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Occurrence index cannot be negative: " + n);
        }
        return switch (frequency) {
            case WEEKLY -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);
            case YEARLY -> start.plusYears(n);
        };
    }
}
