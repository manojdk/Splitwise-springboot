package com.splitwise.recurring;

import com.splitwise.domain.Frequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleTest {

    @Test
    void monthEndDoesNotDriftAfterAShortMonth() {
        LocalDate start = LocalDate.of(2027, 1, 31);

        assertThat(Schedule.occurrence(start, Frequency.MONTHLY, 0)).isEqualTo("2027-01-31");
        assertThat(Schedule.occurrence(start, Frequency.MONTHLY, 1)).isEqualTo("2027-02-28");
        assertThat(Schedule.occurrence(start, Frequency.MONTHLY, 2)).isEqualTo("2027-03-31"); // not the 28th
        assertThat(Schedule.occurrence(start, Frequency.MONTHLY, 3)).isEqualTo("2027-04-30");
        assertThat(Schedule.occurrence(start, Frequency.MONTHLY, 13)).isEqualTo("2028-02-29"); // leap year
    }

    @Test
    void yearlyOnALeapDayFallsOnThe28thInOtherYears() {
        LocalDate start = LocalDate.of(2028, 2, 29);

        assertThat(Schedule.occurrence(start, Frequency.YEARLY, 1)).isEqualTo("2029-02-28");
        assertThat(Schedule.occurrence(start, Frequency.YEARLY, 4)).isEqualTo("2032-02-29");
    }

    @Test
    void weeklyAddsSevenDaysAcrossMonthAndYearEnds() {
        LocalDate start = LocalDate.of(2026, 12, 28);

        assertThat(Schedule.occurrence(start, Frequency.WEEKLY, 1)).isEqualTo("2027-01-04");
        assertThat(Schedule.occurrence(start, Frequency.WEEKLY, 52)).isEqualTo("2027-12-27");
    }

    @Test
    void aNegativeIndexIsAProgrammingError() {
        assertThatThrownBy(() -> Schedule.occurrence(LocalDate.of(2027, 1, 1), Frequency.WEEKLY, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
