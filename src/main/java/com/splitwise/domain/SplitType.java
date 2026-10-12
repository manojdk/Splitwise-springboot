package com.splitwise.domain;

/**
 * How an expense is divided. What each share's {@code value} means depends on the type.
 */
public enum SplitType {
    /** Equal parts; {@code value} is ignored. */
    EQUAL,
    /** {@code value} is the amount owed; the amounts must add up to the total. */
    EXACT,
    /** {@code value} is a percentage; they must add up to 100. */
    PERCENTAGE,
    /** {@code value} is a number of shares (e.g. 2, 1, 1, or 1.5); the total is divided in proportion. */
    SHARES,
    /** {@code value} is an extra amount on top of an equal split of the rest (missing = 0). */
    ADJUSTMENT
}
