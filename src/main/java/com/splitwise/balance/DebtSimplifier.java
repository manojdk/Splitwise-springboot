package com.splitwise.balance;

import com.splitwise.domain.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Greedy minimum-cash-flow debt simplification.
 *
 * <p>Input: each user's net balance (positive = is owed money, negative = owes money); balances must
 * sum to zero. Repeatedly match the largest creditor with the largest debtor and transfer the smaller
 * of the two amounts; one of them is then fully settled. This produces at most {@code n - 1}
 * transfers for {@code n} non-zero balances. Finding the true minimum number of transfers is NP-hard
 * in general; this greedy heuristic is the standard practical approach and is optimal in many common
 * cases but not all.
 *
 * <p>Pure function: no I/O, deterministic output (ties broken by lowest user id).
 */
public final class DebtSimplifier {

    private DebtSimplifier() {
    }

    public static List<Transfer> simplify(Map<Long, BigDecimal> netBalances) {
        BigDecimal sum = netBalances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() != 0) {
            throw new IllegalArgumentException("Net balances must sum to zero but sum to " + sum.toPlainString());
        }

        Comparator<Entry> largestFirst = Comparator.comparing(Entry::amount).reversed()
                .thenComparing(Entry::userId);
        PriorityQueue<Entry> creditors = new PriorityQueue<>(largestFirst);
        PriorityQueue<Entry> debtors = new PriorityQueue<>(largestFirst); // amounts stored as positive

        netBalances.forEach((userId, balance) -> {
            BigDecimal b = Money.of(balance);
            if (b.signum() > 0) {
                creditors.add(new Entry(userId, b));
            } else if (b.signum() < 0) {
                debtors.add(new Entry(userId, b.negate()));
            }
        });

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Entry creditor = creditors.poll();
            Entry debtor = debtors.poll();
            BigDecimal amount = creditor.amount().min(debtor.amount());
            transfers.add(new Transfer(debtor.userId(), creditor.userId(), amount));

            BigDecimal creditLeft = creditor.amount().subtract(amount);
            BigDecimal debtLeft = debtor.amount().subtract(amount);
            if (creditLeft.signum() > 0) {
                creditors.add(new Entry(creditor.userId(), creditLeft));
            }
            if (debtLeft.signum() > 0) {
                debtors.add(new Entry(debtor.userId(), debtLeft));
            }
        }
        return transfers;
    }

    private record Entry(Long userId, BigDecimal amount) {
    }
}
