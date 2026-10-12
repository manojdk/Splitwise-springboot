package com.splitwise.split;

import com.splitwise.exception.InvalidSplitException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentageSplitStrategyTest {

    private final PercentageSplitStrategy strategy = new PercentageSplitStrategy();

    @Test
    void splitsCleanPercentages() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("200"), List.of(
                SplitParticipant.of(1L, "50"),
                SplitParticipant.of(2L, "30"),
                SplitParticipant.of(3L, "20")));

        assertThat(result).containsExactly(
                Map.entry(1L, new BigDecimal("100.00")),
                Map.entry(2L, new BigDecimal("60.00")),
                Map.entry(3L, new BigDecimal("40.00")));
    }

    @Test
    void largestRemainderGetsTheLeftoverCent() {
        // 10.00 * 33.33% = 3.333, 10.00 * 33.33% = 3.333, 10.00 * 33.34% = 3.334
        // floors: 3.33 + 3.33 + 3.33 = 9.99 -> 1 cent left, user 3 has the largest fraction (0.004)
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("10.00"), List.of(
                SplitParticipant.of(1L, "33.33"),
                SplitParticipant.of(2L, "33.33"),
                SplitParticipant.of(3L, "33.34")));

        assertThat(result).containsExactly(
                Map.entry(1L, new BigDecimal("3.33")),
                Map.entry(2L, new BigDecimal("3.33")),
                Map.entry(3L, new BigDecimal("3.34")));
    }

    @Test
    void tiedRemaindersAreBrokenByLowestUserId() {
        // 0.01 at 50/50 -> 0.005 each; one cent to distribute, tie -> user 4 (lower id)
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("0.01"), List.of(
                SplitParticipant.of(9L, "50"),
                SplitParticipant.of(4L, "50")));

        assertThat(result).containsExactly(
                Map.entry(4L, new BigDecimal("0.01")),
                Map.entry(9L, new BigDecimal("0.00")));
    }

    @Test
    void zeroPercentParticipantNeverGetsAStrayCent() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("0.03"), List.of(
                SplitParticipant.of(1L, "0"),
                SplitParticipant.of(2L, "50"),
                SplitParticipant.of(3L, "50")));

        assertThat(result.get(1L)).isEqualByComparingTo("0");
        assertThat(result.get(2L)).isEqualByComparingTo("0.02");
        assertThat(result.get(3L)).isEqualByComparingTo("0.01");
    }

    @Test
    void acceptsFractionalPercentagesSummingTo100() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("1000"), List.of(
                SplitParticipant.of(1L, "12.5"),
                SplitParticipant.of(2L, "87.5")));

        assertThat(result.get(1L)).isEqualByComparingTo("125");
        assertThat(result.get(2L)).isEqualByComparingTo("875");
    }

    @Test
    void sharesAlwaysSumToTotal() {
        Random random = new Random(7);
        for (int run = 0; run < 500; run++) {
            BigDecimal total = BigDecimal.valueOf(1 + random.nextInt(10_000_000), 2);
            int n = 1 + random.nextInt(8);
            // random percentages with 2 decimals that sum to exactly 100.00
            int remaining = 10_000;
            List<SplitParticipant> participants = new ArrayList<>();
            for (long id = 1; id <= n; id++) {
                int basisPoints = id == n ? remaining : random.nextInt(remaining + 1);
                remaining -= basisPoints;
                participants.add(new SplitParticipant(id, BigDecimal.valueOf(basisPoints, 2)));
            }
            Map<Long, BigDecimal> result = strategy.split(total, participants);

            BigDecimal sum = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(total);
            result.values().forEach(v -> assertThat(v.scale()).isEqualTo(2));
        }
    }

    @Test
    void rejectsPercentagesNotSummingTo100() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100"), List.of(
                SplitParticipant.of(1L, "33.33"),
                SplitParticipant.of(2L, "33.33"),
                SplitParticipant.of(3L, "33.33"))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("99.99");
    }

    @Test
    void rejectsPercentageAbove100() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100"), List.of(
                SplitParticipant.of(1L, "150"),
                SplitParticipant.of(2L, "-50"))))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsMissingPercentage() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100"), List.of(
                SplitParticipant.of(1L, "100"),
                SplitParticipant.of(2L))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Missing Percentage");
    }
}
