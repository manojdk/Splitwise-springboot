package com.splitwise.balance;

import com.splitwise.balance.FriendBalanceCalculator.Entry;
import com.splitwise.balance.FriendBalanceCalculator.Item;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests: jqwik generates many random ledgers (up to 6 people, 2 groups plus non-group, random
 * expenses and settlements) and checks that the friend balances always obey the money invariants. When a
 * property fails, jqwik shrinks the input to a minimal failing example.
 */
class FriendBalanceCalculatorPropertyTest {

    private static final List<Long> USERS = List.of(1L, 2L, 3L, 4L, 5L, 6L);

    @Property(tries = 500)
    void friendBalancesAddUpToTheOverallBalance(@ForAll("ledgers") List<Entry> ledger) {
        Map<Long, BigDecimal> overall = FriendBalanceCalculator.netBalances(ledger);
        for (long user : USERS) {
            BigDecimal sumOfFriends = FriendBalanceCalculator.forUser(user, ledger).stream()
                    .map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sumOfFriends).as("user %d", user)
                    .isEqualByComparingTo(overall.getOrDefault(user, BigDecimal.ZERO));
        }
    }

    @Property(tries = 500)
    void whatAOwesBIsExactlyWhatBIsOwedByA(@ForAll("ledgers") List<Entry> ledger) {
        Map<String, BigDecimal> seen = new HashMap<>();
        for (long user : USERS) {
            for (Item item : FriendBalanceCalculator.forUser(user, ledger)) {
                seen.put(user + ">" + item.friendId() + "@" + item.groupId(), item.amount());
            }
        }
        seen.forEach((key, amount) -> {
            String[] parts = key.split("[>@]");
            String mirror = parts[1] + ">" + parts[0] + "@" + parts[2];
            assertThat(seen.get(mirror)).as(key).isNotNull().isEqualByComparingTo(amount.negate());
        });
    }

    @Property(tries = 500)
    void settlingEveryBucketLeavesNothingOutstanding(@ForAll("ledgers") List<Entry> ledger, @ForAll("user") long user) {
        List<Entry> settled = new ArrayList<>(ledger);
        for (Item item : FriendBalanceCalculator.forUser(user, ledger)) {
            // friend owes me (positive): friend pays me; I owe friend (negative): I pay friend
            settled.add(item.amount().signum() > 0
                    ? new Entry(item.groupId(), item.friendId(), Map.of(user, item.amount()))
                    : new Entry(item.groupId(), user, Map.of(item.friendId(), item.amount().negate())));
        }

        assertThat(FriendBalanceCalculator.forUser(user, settled)).isEmpty();
        assertThat(FriendBalanceCalculator.netBalances(settled).getOrDefault(user, BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }

    @Property(tries = 300)
    void bucketsAreNeverZeroAndAlwaysInCents(@ForAll("ledgers") List<Entry> ledger) {
        for (long user : USERS) {
            for (Item item : FriendBalanceCalculator.forUser(user, ledger)) {
                assertThat(item.amount().signum()).isNotZero();
                assertThat(item.amount().scale()).isEqualTo(2);
                assertThat(item.friendId()).isNotEqualTo(user);
            }
        }
    }

    // ------------------------------------------------------------------ generators

    @Provide
    Arbitrary<Long> user() {
        return Arbitraries.of(USERS);
    }

    @Provide
    Arbitrary<List<Entry>> ledgers() {
        return entry().list().ofMinSize(0).ofMaxSize(25);
    }

    private Arbitrary<Entry> entry() {
        Arbitrary<Long> group = Arbitraries.of(10L, 20L).injectNull(0.3);
        Arbitrary<Long> creditor = Arbitraries.of(USERS);
        Arbitrary<Set<Long>> debtors = Arbitraries.of(USERS).set().ofMinSize(1).ofMaxSize(5).map(TreeSet::new);
        Arbitrary<List<BigDecimal>> amounts = Arbitraries.longs().between(1, 1_000_000)
                .map(cents -> BigDecimal.valueOf(cents, 2)).list().ofSize(5);
        return Combinators.combine(group, creditor, debtors, amounts).as((g, c, ds, as) -> {
            Map<Long, BigDecimal> debts = new LinkedHashMap<>();
            int i = 0;
            for (Long debtor : ds) {
                debts.put(debtor, as.get(i++));
            }
            return new Entry(g, c, debts);
        });
    }
}
