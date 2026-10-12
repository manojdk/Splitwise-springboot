package com.splitwise.balance;

import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;
import com.splitwise.domain.Settlement;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes net balances from expenses and settlements.
 *
 * <p>Sign convention: positive = the user is owed money, negative = the user owes money.
 * <ul>
 *   <li>Expense: the payer is credited the full amount; every participant is debited their share
 *       (a payer who is also a participant nets out their own share).</li>
 *   <li>Settlement payer to payee: the payer is credited (their debt shrinks), the payee is debited.</li>
 * </ul>
 * The balances of everybody involved always sum to zero.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    public static Map<Long, BigDecimal> netBalances(Collection<Expense> expenses, Collection<Settlement> settlements) {
        Map<Long, BigDecimal> net = new TreeMap<>();
        for (Expense e : expenses) {
            add(net, e.getPaidBy().getId(), e.getAmount());
            for (ExpenseShare s : e.getShares()) {
                add(net, s.getUser().getId(), s.getAmount().negate());
            }
        }
        for (Settlement s : settlements) {
            add(net, s.getPayer().getId(), s.getAmount());
            add(net, s.getPayee().getId(), s.getAmount().negate());
        }
        return net;
    }

    private static void add(Map<Long, BigDecimal> net, Long userId, BigDecimal delta) {
        net.merge(userId, Money.of(delta), BigDecimal::add);
    }
}
