package com.splitwise.split;

import com.splitwise.domain.Money;
import com.splitwise.domain.SplitType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Turns an expense total and its participants into per-user owed amounts.
 *
 * <p>Contract for every implementation:
 * <ul>
 *   <li>the returned amounts have scale 2 and sum to exactly {@code total};</li>
 *   <li>each amount is a whole number of the currency's smallest unit: a cent, or a whole yen (see
 *       {@link com.splitwise.service.CurrencyRules#decimals});</li>
 *   <li>the result is deterministic (iteration order is ascending user id);</li>
 *   <li>invalid input raises {@link com.splitwise.exception.InvalidSplitException}.</li>
 * </ul>
 */
public interface SplitStrategy {

    SplitType type();

    /** For a currency with cents (2 decimals). */
    default Map<Long, BigDecimal> split(BigDecimal total, List<SplitParticipant> participants) {
        return split(total, participants, Money.SCALE);
    }

    /**
     * @param decimals the decimal places amounts in the currency may have: 2, or 0 for yen and won (then the total,
     *                 any amounts entered, and every part are whole numbers)
     */
    Map<Long, BigDecimal> split(BigDecimal total, List<SplitParticipant> participants, int decimals);
}
