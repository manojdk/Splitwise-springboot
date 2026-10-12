package com.splitwise.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Balance between the current user and one friend.
 *
 * @param balance   positive = the friend owes you; negative = you owe the friend; 0 = settled up
 * @param breakdown where it comes from: one line per group, and one with {@code groupId = null} for expenses
 *                  outside any group
 */
public record FriendBalanceResponse(Long userId, String name, BigDecimal balance, String currency,
                                    List<Item> breakdown) {

    public record Item(Long groupId, String groupName, BigDecimal amount) {
    }
}
