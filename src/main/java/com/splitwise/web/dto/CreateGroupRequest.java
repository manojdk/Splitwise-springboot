package com.splitwise.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The caller becomes the only member; others are added by email afterwards (POST /api/groups/{id}/members). There is
 * deliberately no list of user ids here: it would let anyone pull arbitrary users into a group and read their emails.
 *
 * @param currency ISO 4217 code for everything in the group (default INR); cannot be changed later
 */
public record CreateGroupRequest(@NotBlank @Size(max = 100) String name,
                                 @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code")
                                 String currency) {
}
