package com.splitwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * One participant of a recurring template. An embeddable rather than a map entry on purpose: for an EQUAL split
 * the value is null, and Hibernate does not store null values of a collection map, so the participant would be
 * lost when the template is reloaded.
 */
@Embeddable
public class RecurringShare {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "split_value", precision = 19, scale = 4)
    private BigDecimal value;

    protected RecurringShare() {
    }

    public RecurringShare(Long userId, BigDecimal value) {
        this.userId = userId;
        this.value = value;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getValue() {
        return value;
    }
}
