package com.splitwise.web.dto;

import com.splitwise.domain.Activity;
import com.splitwise.domain.ActivityType;

import java.time.Instant;
import java.util.List;

/**
 * One activity feed entry.
 *
 * @param summary one line, e.g. {@code Bob updated "Dinner"}
 * @param details extra lines: for an edit one line per change (e.g. {@code Amount: 300.00 -> 400.00}), for a new
 *                group its members; otherwise empty
 */
public record ActivityResponse(Long id, ActivityType type, Long actorId, String actorName, Long groupId,
                               Long expenseId, Long settlementId, String summary, List<String> details,
                               Instant createdAt) {

    public static ActivityResponse from(Activity a) {
        List<String> details = a.getDetails() == null ? List.of() : List.of(a.getDetails().split("\n"));
        return new ActivityResponse(a.getId(), a.getType(), a.getActor().getId(), a.getActor().getName(),
                a.getGroupId(), a.getExpenseId(), a.getSettlementId(), a.getSummary(), details, a.getCreatedAt());
    }
}
