package com.splitwise.service;

import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
import com.splitwise.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Which currency an expense or payment is in.
 * <ul>
 *   <li>A group has one currency, chosen when it is created (a trip to Paris in EUR). Everything in the group
 *       is in that currency: leaving it out means the group's currency, giving another one is a 400. That keeps
 *       every group balance a single, meaningful number.</li>
 *   <li>Outside groups, any ISO 4217 currency may be used (default INR). Balances are then kept per currency,
 *       never added across currencies.</li>
 * </ul>
 */
public final class CurrencyRules {

    private CurrencyRules() {
    }

    /** A valid ISO 4217 code (e.g. INR, EUR, USD), or a 400. */
    public static String requireValid(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) {
            throw new BadRequestException("Currency must be a 3-letter ISO 4217 code, e.g. INR");
        }
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown currency: " + code);
        }
        return code;
    }

    /**
     * The decimal places amounts in this currency may have: 0 for those without a smaller unit (JPY, KRW, VND...),
     * otherwise 2. The few with three (KWD, BHD) stay at 2, as before: a thousandth of a dinar is worth too little to
     * matter, and amounts already saved keep their meaning.
     */
    public static int decimals(String code) {
        return Currency.getInstance(code).getDefaultFractionDigits() == 0 ? 0 : Money.SCALE;
    }

    /** 400 unless the amount fits the currency: "JPY amounts have no decimals" for 100.50 yen. */
    public static void requireFits(BigDecimal amount, String code) {
        if (decimals(code) == 0 && !Money.hasValidScale(amount, 0)) {
            throw new BadRequestException(code + " amounts have no decimals");
        }
    }

    /** The currency for something recorded in {@code group} (null = outside groups). */
    public static String resolve(String requested, Group group) {
        if (group != null) {
            if (requested != null && !requested.isBlank() && !requested.equals(group.getCurrency())) {
                throw new BadRequestException("This group uses " + group.getCurrency()
                        + "; everything in it must be in " + group.getCurrency());
            }
            return group.getCurrency();
        }
        return requested == null || requested.isBlank() ? Money.DEFAULT_CURRENCY : requireValid(requested);
    }
}
