package com.splitwise.web.dto;

import java.math.BigDecimal;

/** A suggested payment: {@code from} pays {@code to} the given amount. */
public record TransferResponse(Long fromUserId, String fromName, Long toUserId, String toName, BigDecimal amount,
                               String currency) {
}
