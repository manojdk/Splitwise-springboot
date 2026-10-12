package com.splitwise.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The "friends" screen: everyone you have an open balance with, plus totals.
 *
 * @param youAreOwed sum of what friends owe you
 * @param youOwe     sum of what you owe friends (positive number)
 * @param net        youAreOwed - youOwe; equals GET /api/users/me/balance
 */
public record FriendBalancesResponse(BigDecimal youAreOwed, BigDecimal youOwe, BigDecimal net, String currency,
                                     List<FriendBalanceResponse> friends) {
}
