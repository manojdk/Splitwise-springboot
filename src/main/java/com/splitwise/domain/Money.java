package com.splitwise.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money conventions used throughout the app: 2 decimal places, banker's rounding (HALF_EVEN). */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    public static final String DEFAULT_CURRENCY = "INR";

    private Money() {
    }

    /** Normalises to scale 2 using HALF_EVEN. */
    public static BigDecimal of(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    /** True if the value has at most 2 significant decimal places (10.50 yes, 10.005 no). */
    public static boolean hasValidScale(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= SCALE;
    }
}
