package com.splitwise.web.dto;

import java.util.List;

/**
 * Result of settling up with a friend: the settlements recorded (one per group with an open balance, plus one
 * for non-group expenses) and the balance afterwards (zero).
 */
public record SettleUpResponse(List<SettlementResponse> settlements, FriendBalanceResponse balance) {
}
