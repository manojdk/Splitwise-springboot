package com.splitwise.split;

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
 *   <li>the result is deterministic (iteration order is ascending user id);</li>
 *   <li>invalid input raises {@link com.splitwise.exception.InvalidSplitException}.</li>
 * </ul>
 */
public interface SplitStrategy {

    SplitType type();

    Map<Long, BigDecimal> split(BigDecimal total, List<SplitParticipant> participants);
}
