package com.splitwise.web.dto;

import com.splitwise.domain.Category;
import com.splitwise.domain.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Full replacement of an expense's details and shares (the group cannot change). As a full replacement, a
 * missing {@code category} means GENERAL and missing {@code notes} clears them.
 *
 * @param version the version the client last read (from {@link ExpenseResponse#version()}); if the expense has
 *                changed since, the update is refused with 409 so nobody's edit is silently lost
 */
public record UpdateExpenseRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency,
        @NotNull Long paidById,
        LocalDate date,
        @NotNull SplitType splitType,
        Category category,
        @Size(max = 1000) String notes,
        @NotEmpty List<@Valid @NotNull ShareRequest> shares,
        @NotNull Long version) {
}
