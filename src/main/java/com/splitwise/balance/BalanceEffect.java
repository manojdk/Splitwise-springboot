package com.splitwise.balance;

import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Group;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.User;
import com.splitwise.exception.ConflictException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * How one expense or payment moves each person's balance in its group (positive = they are owed more), so a change
 * can be compared before and after.
 *
 * <p>Used to protect people who have left a group: they can neither see its balances nor record a payment in it, so
 * deleting, restoring or re-splitting a record must not change what they owe or are owed there. Leaving requires a
 * zero balance; this keeps it zero.
 */
public final class BalanceEffect {

    public static final BalanceEffect NONE = new BalanceEffect(Map.of(), Map.of());

    private final Map<Long, BigDecimal> byUser;
    private final Map<Long, String> names;

    private BalanceEffect(Map<Long, BigDecimal> byUser, Map<Long, String> names) {
        this.byUser = byUser;
        this.names = names;
    }

    /** The payer is owed the amount; each participant owes their share. */
    public static BalanceEffect of(User payer, BigDecimal amount, Map<User, BigDecimal> shares) {
        Builder builder = new Builder().add(payer, amount);
        shares.forEach((user, share) -> builder.add(user, share.negate()));
        return builder.build();
    }

    public static BalanceEffect of(Expense expense) {
        Builder builder = new Builder().add(expense.getPaidBy(), expense.getAmount());
        for (ExpenseShare share : expense.getShares()) {
            builder.add(share.getUser(), share.getAmount().negate());
        }
        return builder.build();
    }

    /** Paying someone back: the payer's debt shrinks (their balance rises), the payee's credit shrinks. */
    public static BalanceEffect of(Settlement settlement) {
        return new Builder()
                .add(settlement.getPayer(), settlement.getAmount())
                .add(settlement.getPayee(), settlement.getAmount().negate())
                .build();
    }

    /**
     * Refuses a change from {@code before} to {@code after} that would move the balance of someone who is no longer in
     * the group. Non-group records have no members to leave, so they always pass.
     *
     * @param action what the caller tried, for the message ("delete this expense")
     */
    public static void requireFormerMembersUnchanged(Group group, BalanceEffect before, BalanceEffect after,
                                                     String action) {
        if (group == null) {
            return;
        }
        Map<Long, String> people = new TreeMap<>(before.names);
        people.putAll(after.names);
        people.forEach((userId, name) -> {
            if (!group.hasMember(userId) && before.amountFor(userId).compareTo(after.amountFor(userId)) != 0) {
                throw new ConflictException("Cannot " + action + ": it would change the balance of " + name
                        + ", who has left the group and could not see or settle it");
            }
        });
    }

    private BigDecimal amountFor(Long userId) {
        return byUser.getOrDefault(userId, BigDecimal.ZERO);
    }

    private static final class Builder {
        private final Map<Long, BigDecimal> byUser = new HashMap<>();
        private final Map<Long, String> names = new HashMap<>();

        Builder add(User user, BigDecimal amount) {
            byUser.merge(user.getId(), amount, BigDecimal::add);
            names.put(user.getId(), user.getName());
            return this;
        }

        BalanceEffect build() {
            return new BalanceEffect(Map.copyOf(byUser), Map.copyOf(names));
        }
    }
}
