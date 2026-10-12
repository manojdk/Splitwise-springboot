package com.splitwise.query;

import com.splitwise.domain.Settlement;
import com.splitwise.exception.BadRequestException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Building blocks for settlement list queries. */
public final class SettlementSpecs {

    private SettlementSpecs() {
    }

    public static Specification<Settlement> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Settlement> inGroup(Long groupId) {
        return (root, query, cb) -> cb.equal(root.get("group").get("id"), groupId);
    }

    public static Specification<Settlement> involving(Long userId) {
        return (root, query, cb) -> cb.or(cb.equal(root.get("payer").get("id"), userId),
                cb.equal(root.get("payee").get("id"), userId));
    }

    /** Settlement date between {@code from} and {@code to} (inclusive; null = open). */
    public static Specification<Settlement> between(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("settledOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("settledOn"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
