package com.splitwise.split;

import com.splitwise.exception.InvalidSplitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EqualSplitStrategyTest {

    private final EqualSplitStrategy strategy = new EqualSplitStrategy();

    @Test
    void splitsEvenlyWhenDivisible() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("90"),
                List.of(SplitParticipant.of(1L), SplitParticipant.of(2L), SplitParticipant.of(3L)));

        assertThat(result).containsExactly(
                Map.entry(1L, new BigDecimal("30.00")),
                Map.entry(2L, new BigDecimal("30.00")),
                Map.entry(3L, new BigDecimal("30.00")));
    }

    @Test
    void leftoverCentGoesToLowestUserIdRegardlessOfInputOrder() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("100.00"),
                List.of(SplitParticipant.of(7L), SplitParticipant.of(3L), SplitParticipant.of(5L)));

        assertThat(result).containsExactly(
                Map.entry(3L, new BigDecimal("33.34")),
                Map.entry(5L, new BigDecimal("33.33")),
                Map.entry(7L, new BigDecimal("33.33")));
    }

    @Test
    void twoLeftoverCentsGoToTwoLowestIds() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("0.05"),
                List.of(SplitParticipant.of(1L), SplitParticipant.of(2L), SplitParticipant.of(3L)));

        assertThat(result.values()).containsExactly(
                new BigDecimal("0.02"), new BigDecimal("0.02"), new BigDecimal("0.01"));
    }

    @Test
    void oneCentAmongThreeLeavesOthersAtZero() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("0.01"),
                List.of(SplitParticipant.of(1L), SplitParticipant.of(2L), SplitParticipant.of(3L)));

        assertThat(result.values()).containsExactly(
                new BigDecimal("0.01"), new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test
    void singleParticipantOwesEverything() {
        assertThat(strategy.split(new BigDecimal("12.5"), List.of(SplitParticipant.of(9L))))
                .containsExactly(Map.entry(9L, new BigDecimal("12.50")));
    }

    @Test
    void sharesAlwaysSumToTotal() {
        Random random = new Random(42);
        for (int run = 0; run < 500; run++) {
            BigDecimal total = BigDecimal.valueOf(1 + random.nextInt(1_000_000), 2);
            int n = 1 + random.nextInt(12);
            List<SplitParticipant> participants = new ArrayList<>();
            for (long id = 1; id <= n; id++) {
                participants.add(SplitParticipant.of(id));
            }
            Map<Long, BigDecimal> result = strategy.split(total, participants);

            BigDecimal sum = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(total);
            BigDecimal max = result.values().stream().max(BigDecimal::compareTo).orElseThrow();
            BigDecimal min = result.values().stream().min(BigDecimal::compareTo).orElseThrow();
            assertThat(max.subtract(min)).isLessThanOrEqualTo(new BigDecimal("0.01"));
        }
    }

    @ParameterizedTest
    @CsvSource({"0", "-5", "10.005"})
    void rejectsInvalidAmounts(String amount) {
        assertThatThrownBy(() -> strategy.split(new BigDecimal(amount), List.of(SplitParticipant.of(1L))))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsNoParticipants() {
        assertThatThrownBy(() -> strategy.split(BigDecimal.TEN, List.of()))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("at least one participant");
    }

    @Test
    void rejectsDuplicateParticipants() {
        assertThatThrownBy(() -> strategy.split(BigDecimal.TEN,
                List.of(SplitParticipant.of(1L), SplitParticipant.of(1L))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("more than once");
    }
}
