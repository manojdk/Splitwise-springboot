package com.splitwise.web.dto;

import com.splitwise.domain.Group;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record GroupResponse(Long id, String name, Long createdById, Instant createdAt, List<UserResponse> members) {

    public static GroupResponse from(Group group) {
        List<UserResponse> members = group.getMembers().stream()
                .map(UserResponse::from)
                .sorted(Comparator.comparing(UserResponse::id))
                .toList();
        return new GroupResponse(group.getId(), group.getName(), group.getCreatedBy().getId(),
                group.getCreatedAt(), members);
    }
}
