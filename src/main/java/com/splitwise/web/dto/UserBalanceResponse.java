package com.splitwise.web.dto;

import java.util.List;

/**
 * Overall net balance of one user across all groups and non-group expenses, one entry per currency with a
 * non-zero balance (empty = settled up everywhere). Positive = is owed.
 */
public record UserBalanceResponse(Long userId, List<CurrencyAmount> balances) {
}
