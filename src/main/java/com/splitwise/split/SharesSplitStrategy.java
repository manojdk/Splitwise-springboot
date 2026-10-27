package com.splitwise.split;

import com.splitwise.domain.Money;
import com.splitwise.domain.SplitType;
import com.splitwise.exception.InvalidSplitException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Splits in proportion to a number of shares per person, e.g. a room for a couple and a single: 2, 1.
 * Shares may have up to 2 decimals (1.5) and may be 0 (in the expense, owing nothing), but not all 0.
 * Rounding is the largest-remainder method, so the parts always add up to the total exactly.
 */
@Component
public class SharesSplitStrategy extends AbstractSplitStrategy {

    private static final BigDecimal MAX_SHARES = BigDecimal.valueOf(1_000_000);

    @Override
    public SplitType type() {
        return SplitType.SHARES;
    }

    @Override
    protected Map<Long, BigDecimal> allocate(BigDecimal total, List<SplitParticipant> participants, int decimals) {
        BigDecimal sum = BigDecimal.ZERO;
        for (SplitParticipant p : participants) {
            BigDecimal shares = requireValue(p, "Shares");
            if (!Money.hasValidScale(shares)) {
                throw new InvalidSplitException("Shares for user " + p.userId() + " may have at most 2 decimal places");
            }
            if (shares.compareTo(MAX_SHARES) > 0) {
                throw new InvalidSplitException("Shares for user " + p.userId() + " must not exceed " + MAX_SHARES);
            }
            sum = sum.add(shares);
        }
        if (sum.signum() == 0) {
            throw new InvalidSplitException("At least one participant needs more than 0 shares");
        }
        return Allocation.proportional(total, participants.stream().map(SplitParticipant::userId).toList(),
                participants.stream().map(SplitParticipant::value).toList(), decimals);
    }
}
