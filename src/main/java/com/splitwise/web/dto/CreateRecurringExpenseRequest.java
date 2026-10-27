package com.splitwise.web.dto;

import com.splitwise.domain.Category;
import com.splitwise.domain.Frequency;
import com.splitwise.domain.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import com.splitwise.web.validation.PlausibleDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A recurring expense: the same fields as an expense, plus how often it repeats and from when.
 *
 * @param startDate first occurrence; today or later
 * @param endDate   last day an occurrence may fall on (inclusive); null = no end
 */
public record CreateRecurringExpenseRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency,
        @NotNull Long paidById,
        Long groupId,
        @NotNull SplitType splitType,
        Category category,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 100, message = "must have at most 100 people") List<@Valid @NotNull ShareRequest> shares,
        @NotNull Frequency frequency,
        @NotNull @PlausibleDate LocalDate startDate,
        LocalDate endDate) {
}
