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
 * "Who owes whom" between one user and each of their friends, split per group and per currency.
 *
 * <p>Two kinds of bucket:
 * <ul>
 *   <li><b>Group</b>: taken from the group's simplified debts ({@link DebtSimplifier}), i.e. the same transfers
 *       the group screen suggests, in the group's currency. Using the simplified view (as Splitwise does with
 *       "simplify group debts") keeps it consistent with the group: a member with a zero net balance in a group
 *       owes nobody there.</li>
 *   <li><b>Non-group</b>: direct, pair by pair and currency by currency, from expenses and settlements outside
 *       any group.</li>
 * </ul>
 * Amounts in different currencies are never added together.
 *
 * <p>Invariants (checked by property-based tests), per currency:
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
     * One movement of money: each debtor owes the creditor the given amount, in {@code currency}.
     * An expense is creditor = payer, debtors = the participants' shares; a settlement "payer paid payee x" is
     * creditor = payer, debtor = payee for x.
     *
     * @param groupId null for a non-group expense or settlement
     */
    public record Entry(Long groupId, String currency, long creditorId, Map<Long, BigDecimal> debts) {
        public Entry {
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(debts, "debts");
        }
    }

    /**
     * One non-zero bucket between the user and a friend.
     *
     * @param groupId null for non-group
     * @param amount  positive = the friend owes the user; negative = the user owes the friend
     */
    public record Item(long friendId, Long groupId, String currency, BigDecimal amount) {
    }

    private record Bucket(Long groupId, String currency) {
    }

    private record Pair(long friendId, String currency) {
    }

    /** All non-zero buckets for {@code userId}, ordered by friend, then group (non-group last), then currency. */
    public static List<Item> forUser(long userId, Collection<Entry> entries) {
        Map<Bucket, List<Entry>> groupBuckets = new HashMap<>();
        Map<Pair, BigDecimal> direct = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.groupId() != null) {
                groupBuckets.computeIfAbsent(new Bucket(entry.groupId(), entry.currency()), b -> new ArrayList<>())
                        .add(entry);
                continue;
            }
            entry.debts().forEach((debtorId, amount) -> {
                if (debtorId == entry.creditorId()) {
                    return;
                }
                if (entry.creditorId() == userId) {
                    direct.merge(new Pair(debtorId, entry.currency()), amount, BigDecimal::add);
                } else if (debtorId == userId) {
                    direct.merge(new Pair(entry.creditorId(), entry.currency()), amount.negate(), BigDecimal::add);
                }
            });
        }

        List<Item> items = new ArrayList<>();
        groupBuckets.forEach((bucket, groupEntries) -> {
            for (Transfer t : DebtSimplifier.simplify(netBalances(groupEntries))) {
                if (t.fromUserId() == userId) {
                    items.add(new Item(t.toUserId(), bucket.groupId(), bucket.currency(), Money.of(t.amount().negate())));
                } else if (t.toUserId() == userId) {
                    items.add(new Item(t.fromUserId(), bucket.groupId(), bucket.currency(), Money.of(t.amount())));
                }
            }
        });
        direct.forEach((pair, amount) -> {
            if (amount.signum() != 0) {
                items.add(new Item(pair.friendId(), null, pair.currency(), Money.of(amount)));
            }
        });
        items.sort(Comparator.comparingLong(Item::friendId)
                .thenComparing(Item::groupId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Item::currency));
        return items;
    }

    /** Net balance per user over the entries (positive = is owed money). Pass entries of one currency only. */
    public static Map<Long, BigDecimal> netBalances(Collection<Entry> entries) {
        Map<Long, BigDecimal> net = new TreeMap<>();
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

    /** Net balance per user over the entries in {@code currency}. */
    public static Map<Long, BigDecimal> netBalances(Collection<Entry> entries, String currency) {
        return netBalances(entries.stream().filter(e -> e.currency().equals(currency)).toList());
    }
}
