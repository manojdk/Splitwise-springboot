package com.splitwise.web.dto;

import java.math.BigDecimal;

/** An amount in one currency. Amounts in different currencies are never added together. */
public record CurrencyAmount(String currency, BigDecimal amount) {
}
