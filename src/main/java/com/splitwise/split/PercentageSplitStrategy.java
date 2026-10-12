package com.splitwise.split;

import com.splitwise.domain.Money;
import com.splitwise.domain.SplitType;
import com.splitwise.exception.InvalidSplitException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    protected Map<Long, BigDecimal> allocate(BigDecimal total, List<SplitParticipant> participants) {
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

        record Draft(Long userId, BigDecimal floor, BigDecimal fraction) {
        }
        List<Draft> drafts = new ArrayList<>();
        BigDecimal allocated = Money.ZERO;
        for (SplitParticipant p : participants) {
            BigDecimal exact = total.multiply(p.value()).divide(HUNDRED); // exact: dividing by 100 always terminates
            BigDecimal floor = exact.setScale(Money.SCALE, RoundingMode.DOWN);
            drafts.add(new Draft(p.userId(), floor, exact.subtract(floor)));
            allocated = allocated.add(floor);
        }

        int leftoverCents = total.subtract(allocated).unscaledValue().intValueExact();
        List<Draft> byFraction = drafts.stream()
                .sorted(Comparator.comparing(Draft::fraction).reversed().thenComparing(Draft::userId))
                .toList();

        Map<Long, BigDecimal> extra = new LinkedHashMap<>();
        for (int i = 0; i < leftoverCents; i++) {
            extra.put(byFraction.get(i).userId(), new BigDecimal("0.01"));
        }

        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Draft d : drafts) {
            result.put(d.userId(), d.floor().add(extra.getOrDefault(d.userId(), Money.ZERO)));
        }
        return result;
    }
}
