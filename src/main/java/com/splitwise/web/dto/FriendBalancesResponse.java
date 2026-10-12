package com.splitwise.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The "friends" screen: everyone you have an open balance with, plus totals per currency.
 */
public record FriendBalancesResponse(List<Totals> totals, List<FriendBalanceResponse> friends) {

    /**
     * @param youAreOwed sum of what friends owe you in this currency
     * @param youOwe     sum of what you owe friends in this currency (positive number)
     * @param net        youAreOwed - youOwe; equals your overall balance in this currency
     */
    public record Totals(String currency, BigDecimal youAreOwed, BigDecimal youOwe, BigDecimal net) {
    }
}
