package com.splitwise.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param value ignored for EQUAL, the owed amount for EXACT, the percentage (0-100) for PERCENTAGE
 */
public record ShareRequest(@NotNull Long userId, BigDecimal value) {
}
