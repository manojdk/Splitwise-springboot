package com.splitwise.web.dto;

import java.math.BigDecimal;

/** Net balance of one member within a group. Positive = is owed money, negative = owes money. */
public record BalanceResponse(Long userId, String name, BigDecimal netBalance, String currency) {
}
