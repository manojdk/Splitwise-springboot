package com.splitwise.balance;

import java.math.BigDecimal;

/** "fromUserId pays toUserId amount". */
public record Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
}
