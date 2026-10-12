package com.splitwise.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The caller always becomes a member; {@code memberIds} optionally adds more users up front. */
public record CreateGroupRequest(@NotBlank @Size(max = 100) String name, List<Long> memberIds) {
}
