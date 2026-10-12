package com.splitwise.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Records that {@code fromUserId} paid {@code toUserId}. The caller must be one of the two. */
public record CreateSettlementRequest(
        @NotNull Long fromUserId,
        @NotNull Long toUserId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        Long groupId,
        LocalDate date,
        @Size(max = 255) String note) {
}
