package com.splitwise.split;

import java.math.BigDecimal;

/**
 * One participant of a split.
 *
 * @param userId the participant
 * @param value  meaning depends on the split type: ignored for EQUAL, the exact amount for EXACT,
 *               the percentage (0-100) for PERCENTAGE, the number of shares for SHARES, the extra amount
 *               for ADJUSTMENT (missing = 0)
 */
public record SplitParticipant(Long userId, BigDecimal value) {

    public static SplitParticipant of(Long userId) {
        return new SplitParticipant(userId, null);
    }

    public static SplitParticipant of(Long userId, String value) {
        return new SplitParticipant(userId, new BigDecimal(value));
    }
}
