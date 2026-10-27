package com.splitwise.split;

import com.splitwise.domain.Money;
import com.splitwise.domain.SplitType;
import com.splitwise.exception.InvalidSplitException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Each participant owes an explicitly given amount; the amounts must add up to the total exactly. */
@Component
public class ExactSplitStrategy extends AbstractSplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EXACT;
    }

    @Override
    protected Map<Long, BigDecimal> allocate(BigDecimal total, List<SplitParticipant> participants, int decimals) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal sum = Money.ZERO;
        for (SplitParticipant p : participants) {
            BigDecimal value = requireValue(p, "Amount");
            if (!Money.hasValidScale(value, decimals)) {
                throw new InvalidSplitException("Amount for user " + p.userId() + " " + Money.decimalsRule(decimals));
            }
            BigDecimal amount = Money.of(value);
            result.put(p.userId(), amount);
            sum = sum.add(amount);
        }
        if (sum.compareTo(total) != 0) {
            throw new InvalidSplitException(
                    "Exact shares add up to " + sum.toPlainString() + " but the expense amount is " + total.toPlainString());
        }
        return result;
    }
}
