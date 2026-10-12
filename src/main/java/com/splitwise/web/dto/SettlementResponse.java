package com.splitwise.web.dto;

import com.splitwise.domain.Settlement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementResponse(Long id, Long fromUserId, String fromName, Long toUserId, String toName,
                                 BigDecimal amount, String currency, @Schema(nullable = true) Long groupId, LocalDate date, @Schema(nullable = true) String note,
                                 long version, boolean deleted) {

    public static SettlementResponse from(Settlement s) {
        return new SettlementResponse(s.getId(), s.getPayer().getId(), s.getPayer().getName(),
                s.getPayee().getId(), s.getPayee().getName(), s.getAmount(), s.getCurrency(),
                s.getGroup() == null ? null : s.getGroup().getId(), s.getSettledOn(), s.getNote(),
                s.getVersion(), s.isDeleted());
    }
}
