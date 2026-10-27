package com.splitwise.split;

import com.splitwise.domain.SplitType;
import com.splitwise.exception.InvalidSplitException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Splits by percentage. Percentages must sum to exactly 100.
 *
 * <p>Rounding uses the largest-remainder method: every share is first truncated to whole cents, then
 * the cents lost to truncation are given, one each, to the participants with the largest discarded
 * fractions (ties broken by lowest user id). The shares therefore always sum to the total exactly,
 * and a participant with 0% never receives a stray cent.
 */
@Component
public class PercentageSplitStrategy extends AbstractSplitStrategy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_PERCENT_SCALE = 4;

    @Override
    public SplitType type() {
        return SplitType.PERCENTAGE;
    }

    @Override
    protected Map<Long, BigDecimal> allocate(BigDecimal total, List<SplitParticipant> participants, int decimals) {
        BigDecimal percentSum = BigDecimal.ZERO;
        for (SplitParticipant p : participants) {
            BigDecimal pct = requireValue(p, "Percentage");
            if (pct.compareTo(HUNDRED) > 0) {
                throw new InvalidSplitException("Percentage for user " + p.userId() + " must not exceed 100");
            }
            if (pct.stripTrailingZeros().scale() > MAX_PERCENT_SCALE) {
                throw new InvalidSplitException("Percentage for user " + p.userId() + " has more than 4 decimal places");
            }
            percentSum = percentSum.add(pct);
        }
        if (percentSum.compareTo(HUNDRED) != 0) {
            throw new InvalidSplitException("Percentages add up to " + percentSum.stripTrailingZeros().toPlainString()
                    + " but must add up to 100");
        }
        return Allocation.proportional(total, participants.stream().map(SplitParticipant::userId).toList(),
                participants.stream().map(SplitParticipant::value).toList(), decimals);
    }
}
