package com.splitwise.split;

import com.splitwise.domain.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
        return Allocation.equal(total, participants.stream().map(SplitParticipant::userId).toList());
    }
}
