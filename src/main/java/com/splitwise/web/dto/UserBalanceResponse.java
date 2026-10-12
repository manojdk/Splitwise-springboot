package com.splitwise.web.dto;

import java.math.BigDecimal;

/** Overall net balance of one user across all groups and non-group expenses. Positive = is owed. */
public record UserBalanceResponse(Long userId, BigDecimal netBalance, String currency) {
}
