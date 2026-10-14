package com.splitwise.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the user can change about themselves. The email is not (yet): changing it needs the new one confirmed. */
public record UpdateProfileRequest(@NotBlank @Size(max = 100) String name) {
}
