package com.splitwise.web.dto;

import com.splitwise.domain.Category;
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

public record CreateExpenseRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency,
        @NotNull Long paidById,
        Long groupId,
        @PlausibleDate LocalDate date,
        @NotNull SplitType splitType,
        Category category,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 100, message = "must have at most 100 people") List<@Valid @NotNull ShareRequest> shares) {
}
