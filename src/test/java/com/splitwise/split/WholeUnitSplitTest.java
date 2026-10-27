package com.splitwise.split;

import com.splitwise.exception.InvalidSplitException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Currencies without a minor unit (yen, won): every part is a whole number, and still adds up to the total. */
class WholeUnitSplitTest {

    private static final int NO_DECIMALS = 0;

    @Test
    void equalPartsAreWholeAndTheLeftoverGoesToTheLowestIds() {
        Map<Long, BigDecimal> owed = new EqualSplitStrategy().split(new BigDecimal("100"),
                List.of(SplitParticipant.of(3L), SplitParticipant.of(1L), SplitParticipant.of(2L)), NO_DECIMALS);

        assertThat(owed).containsExactly(Map.entry(1L, new BigDecimal("34.00")), Map.entry(2L, new BigDecimal("33.00")),
                Map.entry(3L, new BigDecimal("33.00")));
    }

    @Test
    void proportionalPartsAreWhole() {
        Map<Long, BigDecimal> owed = new PercentageSplitStrategy().split(new BigDecimal("1000"), List.of(
                new SplitParticipant(1L, new BigDecimal("33.3333")), new SplitParticipant(2L, new BigDecimal("33.3333")),
                new SplitParticipant(3L, new BigDecimal("33.3334"))), NO_DECIMALS);
        assertThat(owed.values()).allSatisfy(v -> assertThat(v.stripTrailingZeros().scale()).isLessThanOrEqualTo(0));
        assertThat(owed.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1000");

        // Shares stay weights: 1.5 shares is fine, the parts are still whole yen
        assertThat(new SharesSplitStrategy().split(new BigDecimal("101"), List.of(
                new SplitParticipant(1L, new BigDecimal("1.5")), new SplitParticipant(2L, new BigDecimal("1"))),
                NO_DECIMALS)).containsExactly(Map.entry(1L, new BigDecimal("61.00")), Map.entry(2L, new BigDecimal("40.00")));
    }

    @Test
    void adjustmentsAndTheRestAreWhole() {
        Map<Long, BigDecimal> owed = new AdjustmentSplitStrategy().split(new BigDecimal("1001"), Arrays.asList(
                new SplitParticipant(1L, new BigDecimal("300")), new SplitParticipant(2L, null)), NO_DECIMALS);
        assertThat(owed).containsExactly(Map.entry(1L, new BigDecimal("651.00")), Map.entry(2L, new BigDecimal("350.00")));
    }

    @Test
    void decimalsAreRefusedWhereverMoneyIsEntered() {
        List<SplitParticipant> two = List.of(SplitParticipant.of(1L), SplitParticipant.of(2L));
        assertThatThrownBy(() -> new EqualSplitStrategy().split(new BigDecimal("100.50"), two, NO_DECIMALS))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Expense amount must be a whole number");
        assertThatThrownBy(() -> new ExactSplitStrategy().split(new BigDecimal("100"), List.of(
                new SplitParticipant(1L, new BigDecimal("50.5")), new SplitParticipant(2L, new BigDecimal("49.5"))),
                NO_DECIMALS))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Amount for user 1 must be a whole number");
        assertThatThrownBy(() -> new AdjustmentSplitStrategy().split(new BigDecimal("100"), Arrays.asList(
                new SplitParticipant(1L, new BigDecimal("0.5")), new SplitParticipant(2L, null)), NO_DECIMALS))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Adjustment for user 1 must be a whole number");
        // 100.00 is a whole number written with decimals: fine
        assertThat(new EqualSplitStrategy().split(new BigDecimal("100.00"), two, NO_DECIMALS)).hasSize(2);
    }
}
