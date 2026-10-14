package com.splitwise.web.dto;

import com.splitwise.web.validation.Password;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Password String newPassword) {
}
