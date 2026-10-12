package com.splitwise.query;

import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Building blocks for expense list queries (JPA Criteria, so filters combine without string concatenation). */
public final class ExpenseSpecs {

    private ExpenseSpecs() {
    }

    public static Specification<Expense> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Expense> inGroup(Long groupId) {
        return (root, query, cb) -> cb.equal(root.get("group").get("id"), groupId);
    }

    /** The user paid for it or has a share in it. */
    public static Specification<Expense> involving(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> shares = query.subquery(Long.class);
            var share = shares.from(ExpenseShare.class);
            shares.select(share.get("expense").get("id")).where(cb.equal(share.get("user").get("id"), userId));
            return cb.or(cb.equal(root.get("paidBy").get("id"), userId), root.get("id").in(shares));
        };
    }

    public static Specification<Expense> matching(ExpenseFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), filter.to()));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.paidBy() != null) {
                predicates.add(cb.equal(root.get("paidBy").get("id"), filter.paidBy()));
            }
            if (filter.q() != null) {
                // The user's text is a literal: escape the LIKE wildcards so "50%" means "50%"
                String literal = filter.q().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("description")), "%" + literal + "%", '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
