package com.splitwise.split;

import com.splitwise.exception.InvalidSplitException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Worked examples for the SHARES and ADJUSTMENT split types (the property tests cover the general rules).
 */
class SharesAndAdjustmentSplitStrategyTest {

    private final SharesSplitStrategy shares = new SharesSplitStrategy();
    private final AdjustmentSplitStrategy adjustment = new AdjustmentSplitStrategy();

    // ------------------------------------------------------------------ SHARES

    @Test
    void aCoupleAndASingleShareARoomTwoToOne() {
        assertThat(shares.split(new BigDecimal("300.00"), List.of(
                SplitParticipant.of(1L, "2"), SplitParticipant.of(2L, "1"))))
                .containsExactly(entry(1L, money("200.00")), entry(2L, money("100.00")));
    }

    @Test
    void equalSharesRoundLikeAnEqualSplit() {
        assertThat(shares.split(new BigDecimal("100.00"), List.of(
                SplitParticipant.of(3L, "1"), SplitParticipant.of(1L, "1"), SplitParticipant.of(2L, "1"))))
                .containsExactly(entry(1L, money("33.34")), entry(2L, money("33.33")), entry(3L, money("33.33")));
    }

    @Test
    void fractionalAndZeroSharesWork() {
        Map<Long, BigDecimal> owed = shares.split(new BigDecimal("10.00"), List.of(
                SplitParticipant.of(1L, "1.5"), SplitParticipant.of(2L, "1"), SplitParticipant.of(3L, "0")));

        assertThat(owed).containsExactly(entry(1L, money("6.00")), entry(2L, money("4.00")), entry(3L, money("0.00")));
    }

    @Test
    void theLeftoverCentGoesToTheLargestRemainder() {
        // 0.10 split 1:2 = 0.0333... and 0.0666...: floors 0.03 + 0.06 = 0.09, the cent goes to the larger remainder
        assertThat(shares.split(new BigDecimal("0.10"), List.of(
                SplitParticipant.of(1L, "1"), SplitParticipant.of(2L, "2"))))
                .containsExactly(entry(1L, money("0.03")), entry(2L, money("0.07")));
    }

    @Test
    void invalidSharesAreRejected() {
        assertThatThrownBy(() -> shares.split(new BigDecimal("10.00"), List.of(
                SplitParticipant.of(1L, "0"), SplitParticipant.of(2L, "0"))))
                .isInstanceOf(InvalidSplitException.class).hasMessageContaining("more than 0 shares");
        assertThatThrownBy(() -> shares.split(new BigDecimal("10.00"), List.of(SplitParticipant.of(1L, "-1"))))
                .isInstanceOf(InvalidSplitException.class);
        assertThatThrownBy(() -> shares.split(new BigDecimal("10.00"), List.of(SplitParticipant.of(1L))))
                .isInstanceOf(InvalidSplitException.class).hasMessageContaining("Missing Shares");
        assertThatThrownBy(() -> shares.split(new BigDecimal("10.00"), List.of(SplitParticipant.of(1L, "1.234"))))
                .isInstanceOf(InvalidSplitException.class);
    }

    // ------------------------------------------------------------------ ADJUSTMENT

    @Test
    void theOneWhoHadDessertPaysExtraAndTheRestIsSplitEqually() {
        Map<Long, BigDecimal> owed = adjustment.split(new BigDecimal("1000.00"), List.of(
                SplitParticipant.of(1L), SplitParticipant.of(2L, "100"), SplitParticipant.of(3L),
                SplitParticipant.of(4L)));

        assertThat(owed).containsExactly(entry(1L, money("225.00")), entry(2L, money("325.00")),
                entry(3L, money("225.00")), entry(4L, money("225.00")));
    }

    @Test
    void theRestIsRoundedLikeAnEqualSplit() {
        // 100.00 - 0.01 = 99.99 / 3 = 33.33 each, then user 1 adds the 0.01
        assertThat(adjustment.split(new BigDecimal("100.00"), List.of(
                SplitParticipant.of(1L, "0.01"), SplitParticipant.of(2L), SplitParticipant.of(3L))))
                .containsExactly(entry(1L, money("33.34")), entry(2L, money("33.33")), entry(3L, money("33.33")));
    }

    @Test
    void adjustmentsThatUseUpTheWholeAmountLeaveNothingToShare() {
        assertThat(adjustment.split(new BigDecimal("50.00"), List.of(
                SplitParticipant.of(1L, "30"), SplitParticipant.of(2L, "20"), SplitParticipant.of(3L))))
                .containsExactly(entry(1L, money("30.00")), entry(2L, money("20.00")), entry(3L, money("0.00")));
    }

    @Test
    void invalidAdjustmentsAreRejected() {
        assertThatThrownBy(() -> adjustment.split(new BigDecimal("50.00"), List.of(
                SplitParticipant.of(1L, "40"), SplitParticipant.of(2L, "20"))))
                .isInstanceOf(InvalidSplitException.class).hasMessageContaining("more than the expense amount");
        assertThatThrownBy(() -> adjustment.split(new BigDecimal("50.00"), List.of(SplitParticipant.of(1L, "-5"))))
                .isInstanceOf(InvalidSplitException.class);
        assertThatThrownBy(() -> adjustment.split(new BigDecimal("50.00"), List.of(SplitParticipant.of(1L, "0.001"))))
                .isInstanceOf(InvalidSplitException.class);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
