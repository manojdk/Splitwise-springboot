package com.splitwise.web.dto;

import java.math.BigDecimal;

public record ShareResponse(Long userId, String name, BigDecimal amount) {
}
