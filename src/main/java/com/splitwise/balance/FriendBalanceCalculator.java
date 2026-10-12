package com.splitwise.balance;

import com.splitwise.domain.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * "Who owes whom" between one user and each of their friends, split per group.
 *
 * <p>Two kinds of bucket:
 * <ul>
 *   <li><b>Group</b>: taken from the group's simplified debts ({@link DebtSimplifier}), i.e. the same transfers
 *       the group screen suggests. Using the simplified view (as Splitwise does with "simplify group debts")
 *       keeps it consistent with the group: a member with a zero net balance in a group owes nobody there.</li>
 *   <li><b>Non-group</b>: direct, pair by pair, from expenses and settlements outside any group.</li>
 * </ul>
 *
 * <p>Invariants (checked by property-based tests):
 * <ol>
 *   <li>The sum of a user's friend balances equals their overall net balance.</li>
 *   <li>Antisymmetry: A's balance with B in a bucket is exactly minus B's balance with A.</li>
 *   <li>Settling every bucket leaves nothing outstanding.</li>
 * </ol>
 *
 * <p>Pure function: no Spring, no I/O.
 */
public final class FriendBalanceCalculator {

    private FriendBalanceCalculator() {
    }

    /**
     * One movement of money: each debtor owes the creditor the given amount.
     * An expense is creditor = payer, debtors = the other participants' shares; a settlement
     * "payer paid payee x" is creditor = payer, debtor = payee for x.
     *
     * @param groupId null for a non-group expense or settlement
     */
    public record Entry(Long groupId, long creditorId, Map<Long, BigDecimal> debts) {
        public Entry {
            Objects.requireNonNull(debts, "debts");
        }
    }

    /**
     * One non-zero bucket between the user and a friend.
     *
     * @param groupId null for non-group
     * @param amount  positive = the friend owes the user; negative = the user owes the friend
     */
    public record Item(long friendId, Long groupId, BigDecimal amount) {
    }

    /** All non-zero buckets for {@code userId}, ordered by friend id, then group id (non-group last). */
    public static List<Item> forUser(long userId, Collection<Entry> entries) {
        Map<Long, List<Entry>> byGroup = new TreeMap<>();
        Map<Long, BigDecimal> direct = new TreeMap<>();
        for (Entry entry : entries) {
            if (entry.groupId() != null) {
                byGroup.computeIfAbsent(entry.groupId(), g -> new ArrayList<>()).add(entry);
                continue;
            }
            entry.debts().forEach((debtorId, amount) -> {
                if (debtorId == entry.creditorId()) {
                    return;
                }
                if (entry.creditorId() == userId) {
                    direct.merge(debtorId, amount, BigDecimal::add);
                } else if (debtorId == userId) {
                    direct.merge(entry.creditorId(), amount.negate(), BigDecimal::add);
                }
            });
        }

        List<Item> items = new ArrayList<>();
        byGroup.forEach((groupId, groupEntries) -> {
            for (Transfer t : DebtSimplifier.simplify(netBalances(groupEntries))) {
                if (t.fromUserId() == userId) {
                    items.add(new Item(t.toUserId(), groupId, Money.of(t.amount().negate())));
                } else if (t.toUserId() == userId) {
                    items.add(new Item(t.fromUserId(), groupId, Money.of(t.amount())));
                }
            }
        });
        direct.forEach((friendId, amount) -> {
            if (amount.signum() != 0) {
                items.add(new Item(friendId, null, Money.of(amount)));
            }
        });
        items.sort(Comparator.comparingLong(Item::friendId)
                .thenComparing(Item::groupId, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    /** Net balance per user over the entries (positive = is owed money). Sums to zero. */
    public static Map<Long, BigDecimal> netBalances(Collection<Entry> entries) {
        Map<Long, BigDecimal> net = new HashMap<>();
        for (Entry entry : entries) {
            entry.debts().forEach((debtorId, amount) -> {
                if (debtorId == entry.creditorId()) {
                    return;
                }
                net.merge(entry.creditorId(), amount, BigDecimal::add);
                net.merge(debtorId, amount.negate(), BigDecimal::add);
            });
        }
        net.replaceAll((id, amount) -> Money.of(amount));
        return net;
    }
}
