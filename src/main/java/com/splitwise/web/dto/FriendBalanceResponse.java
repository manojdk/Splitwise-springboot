package com.splitwise.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Balance between the current user and one friend.
 *
 * @param balances  per currency: positive = the friend owes you, negative = you owe the friend (currencies that
 *                  net to zero are left out; empty = settled up)
 * @param breakdown where it comes from: one line per group (in the group's currency), and one per currency with
 *                  {@code groupId = null} for expenses outside any group
 */
public record FriendBalanceResponse(Long userId, String name, List<CurrencyAmount> balances, List<Item> breakdown) {

    public record Item(Long groupId, String groupName, String currency, BigDecimal amount) {
    }
}
