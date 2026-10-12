package com.splitwise.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The caller always becomes a member; {@code memberIds} optionally adds more users up front.
 *
 * @param currency ISO 4217 code for everything in the group (default INR); cannot be changed later
 */
public record CreateGroupRequest(@NotBlank @Size(max = 100) String name, List<Long> memberIds,
                                 @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code")
                                 String currency) {
}
