package com.splitwise.split;

import com.splitwise.exception.InvalidSplitException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExactSplitStrategyTest {

    private final ExactSplitStrategy strategy = new ExactSplitStrategy();

    @Test
    void acceptsSharesThatSumToTotal() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("100.00"), List.of(
                SplitParticipant.of(2L, "60.5"),
                SplitParticipant.of(1L, "39.50")));

        assertThat(result).containsExactly(
                Map.entry(1L, new BigDecimal("39.50")),
                Map.entry(2L, new BigDecimal("60.50")));
    }

    @Test
    void allowsZeroShareForAParticipant() {
        Map<Long, BigDecimal> result = strategy.split(new BigDecimal("10"), List.of(
                SplitParticipant.of(1L, "10"),
                SplitParticipant.of(2L, "0")));

        assertThat(result.get(2L)).isEqualByComparingTo("0");
    }

    @Test
    void rejectsSharesThatSumToLess() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100"), List.of(
                SplitParticipant.of(1L, "50"),
                SplitParticipant.of(2L, "49.99"))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("99.99")
                .hasMessageContaining("100.00");
    }

    @Test
    void rejectsSharesThatSumToMore() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("100"), List.of(
                SplitParticipant.of(1L, "50"),
                SplitParticipant.of(2L, "50.01"))))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsMissingValue() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("10"), List.of(
                SplitParticipant.of(1L, "10"),
                SplitParticipant.of(2L))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("Missing Amount for user 2");
    }

    @Test
    void rejectsNegativeShare() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("10"), List.of(
                SplitParticipant.of(1L, "15"),
                SplitParticipant.of(2L, "-5"))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void rejectsSubCentPrecision() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("10"), List.of(
                SplitParticipant.of(1L, "5.005"),
                SplitParticipant.of(2L, "4.995"))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("2 decimal places");
    }
}
