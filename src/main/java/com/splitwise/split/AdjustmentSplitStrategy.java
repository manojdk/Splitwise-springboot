package com.splitwise.split;

import com.splitwise.domain.Money;
import com.splitwise.domain.SplitType;
import com.splitwise.exception.InvalidSplitException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Equal split with adjustments: each person may owe an extra amount (for example the one who ordered the
 * dessert); the rest is split equally among everyone.
 *
 * <p>owed(i) = adjustment(i) + an equal part of (total - sum of adjustments), with the equal parts rounded the
 * same way as {@link EqualSplitStrategy}. A missing adjustment is 0; adjustments cannot be negative and cannot
 * add up to more than the total.
 */
@Component
public class AdjustmentSplitStrategy extends AbstractSplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.ADJUSTMENT;
    }

    @Override
    protected Map<Long, BigDecimal> allocate(BigDecimal total, List<SplitParticipant> participants, int decimals) {
        Map<Long, BigDecimal> adjustments = new LinkedHashMap<>();
        BigDecimal adjustmentSum = Money.ZERO;
        for (SplitParticipant p : participants) {
            BigDecimal adjustment = p.value() == null ? Money.ZERO : requireValue(p, "Adjustment");
            if (!Money.hasValidScale(adjustment, decimals)) {
                throw new InvalidSplitException("Adjustment for user " + p.userId() + " " + Money.decimalsRule(decimals));
            }
            adjustments.put(p.userId(), Money.of(adjustment));
            adjustmentSum = adjustmentSum.add(Money.of(adjustment));
        }
        if (adjustmentSum.compareTo(total) > 0) {
            throw new InvalidSplitException("Adjustments add up to " + adjustmentSum.toPlainString()
                    + ", more than the expense amount " + total.toPlainString());
        }
        Map<Long, BigDecimal> owed = new LinkedHashMap<>();
        BigDecimal rest = total.subtract(adjustmentSum);
        if (rest.signum() == 0) {
            owed.putAll(adjustments);
            return owed;
        }
        Allocation.equal(rest, List.copyOf(adjustments.keySet()), decimals)
                .forEach((userId, part) -> owed.put(userId, part.add(adjustments.get(userId))));
        return owed;
    }
}
