package com.splitwise.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import com.splitwise.web.validation.PlausibleDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records that {@code fromUserId} paid {@code toUserId}. The caller must be one of the two.
 *
 * @param currency in a group: the group's currency (may be left out); outside groups: any ISO code, default INR
 */
public record CreateSettlementRequest(
        @NotNull Long fromUserId,
        @NotNull Long toUserId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        Long groupId,
        @PlausibleDate LocalDate date,
        @Size(max = 255) String note,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency) {
}
