package com.splitwise.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @param amount what this person owes
 * @param value  what was entered for them: the percentage, number of shares, exact amount or adjustment; null
 *               for an equal split, an empty adjustment, or an expense created before values were kept
 */
public record ShareResponse(Long userId, String name, BigDecimal amount, @Schema(nullable = true) BigDecimal value) {
}
