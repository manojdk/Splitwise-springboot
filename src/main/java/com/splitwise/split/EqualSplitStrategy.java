package com.splitwise.split;

import com.splitwise.domain.Money;
import com.splitwise.domain.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits the total equally. Leftover cents (total in cents mod n) are handed out one cent each to
 * the participants with the lowest user ids, so 100.00 / 3 = 33.34, 33.33, 33.33 every time.
 */
@Component
public class EqualSplitStrategy extends AbstractSplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EQUAL;
    }

    @Override
    protected Map<Long, BigDecimal> allocate(BigDecimal total, List<SplitParticipant> participants) {
        BigInteger cents = total.unscaledValue();
        BigInteger n = BigInteger.valueOf(participants.size());
        BigInteger[] divRem = cents.divideAndRemainder(n);
        BigInteger base = divRem[0];
        int remainder = divRem[1].intValueExact();

        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            BigInteger share = i < remainder ? base.add(BigInteger.ONE) : base;
            result.put(participants.get(i).userId(), new BigDecimal(share, Money.SCALE));
        }
        return result;
    }
}
