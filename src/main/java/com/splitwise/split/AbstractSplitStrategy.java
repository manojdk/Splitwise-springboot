package com.splitwise.split;

import com.splitwise.domain.Money;
import com.splitwise.exception.InvalidSplitException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared input validation; subclasses only implement the allocation rule. */
abstract class AbstractSplitStrategy implements SplitStrategy {

    @Override
    public final Map<Long, BigDecimal> split(BigDecimal total, List<SplitParticipant> participants) {
        if (total == null || total.signum() <= 0) {
            throw new InvalidSplitException("Expense amount must be greater than zero");
        }
        if (!Money.hasValidScale(total)) {
            throw new InvalidSplitException("Expense amount must have at most 2 decimal places");
        }
        if (participants == null || participants.isEmpty()) {
            throw new InvalidSplitException("An expense needs at least one participant");
        }
        Set<Long> seen = new HashSet<>();
        for (SplitParticipant p : participants) {
            if (p == null || p.userId() == null) {
                throw new InvalidSplitException("Every share must reference a user");
            }
            if (!seen.add(p.userId())) {
                throw new InvalidSplitException("User " + p.userId() + " appears more than once in the split");
            }
        }
        List<SplitParticipant> sorted = participants.stream()
                .sorted(Comparator.comparing(SplitParticipant::userId))
                .toList();
        return allocate(Money.of(total), sorted);
    }

    /**
     * @param total        positive amount with scale 2
     * @param participants non-empty, unique, sorted by user id
     */
    protected abstract Map<Long, BigDecimal> allocate(BigDecimal total, List<SplitParticipant> participants);

    protected static BigDecimal requireValue(SplitParticipant p, String what) {
        if (p.value() == null) {
            throw new InvalidSplitException("Missing " + what + " for user " + p.userId());
        }
        if (p.value().signum() < 0) {
            throw new InvalidSplitException(what + " for user " + p.userId() + " must not be negative");
        }
        return p.value();
    }
}
