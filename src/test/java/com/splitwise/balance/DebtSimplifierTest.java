package com.splitwise.balance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSimplifierTest {

    private static BigDecimal m(String v) {
        return new BigDecimal(v);
    }

    private static Transfer t(long from, long to, String amount) {
        return new Transfer(from, to, new BigDecimal(amount).setScale(2));
    }

    @Test
    void noBalancesMeansNoTransfers() {
        assertThat(DebtSimplifier.simplify(Map.of())).isEmpty();
    }

    @Test
    void alreadySettledGroupNeedsNoTransfers() {
        assertThat(DebtSimplifier.simplify(Map.of(1L, m("0"), 2L, m("0.00"), 3L, m("0")))).isEmpty();
    }

    @Test
    void singleDebtIsPassedThrough() {
        assertThat(DebtSimplifier.simplify(Map.of(1L, m("25.50"), 2L, m("-25.50"))))
                .containsExactly(t(2, 1, "25.50"));
    }

    @Test
    void chainCollapsesIntoOneTransfer() {
        // A owes B 10 and B owes C 10  ->  nets: A -10, B 0, C +10  ->  A pays C directly
        Map<Long, BigDecimal> net = Map.of(1L, m("-10"), 2L, m("0"), 3L, m("10"));

        assertThat(DebtSimplifier.simplify(net)).containsExactly(t(1, 3, "10"));
    }

    @Test
    void pureCycleCancelsOut() {
        // A owes B 10, B owes C 10, C owes A 10  ->  every net balance is zero
        Map<Long, BigDecimal> net = new HashMap<>();
        net.merge(1L, m("-10"), BigDecimal::add);
        net.merge(2L, m("10"), BigDecimal::add);
        net.merge(2L, m("-10"), BigDecimal::add);
        net.merge(3L, m("10"), BigDecimal::add);
        net.merge(3L, m("-10"), BigDecimal::add);
        net.merge(1L, m("10"), BigDecimal::add);

        assertThat(DebtSimplifier.simplify(net)).isEmpty();
    }

    @Test
    void unevenCycleLeavesOnlyTheDifference() {
        // A owes B 30, B owes C 20, C owes A 10 -> nets: A -20, B +10, C +10
        Map<Long, BigDecimal> net = Map.of(1L, m("-20"), 2L, m("10"), 3L, m("10"));

        assertThat(DebtSimplifier.simplify(net)).containsExactly(t(1, 2, "10"), t(1, 3, "10"));
    }

    @Test
    void oneCreditorManyDebtors() {
        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        net.put(1L, m("90"));
        net.put(2L, m("-30"));
        net.put(3L, m("-50"));
        net.put(4L, m("-10"));

        assertThat(DebtSimplifier.simplify(net))
                .containsExactly(t(3, 1, "50"), t(2, 1, "30"), t(4, 1, "10"));
    }

    @Test
    void matchesLargestCreditorWithLargestDebtor() {
        // nets: A +40, B +10, C -30, D -20
        Map<Long, BigDecimal> net = Map.of(1L, m("40"), 2L, m("10"), 3L, m("-30"), 4L, m("-20"));

        assertThat(DebtSimplifier.simplify(net))
                .containsExactly(t(3, 1, "30"), t(4, 1, "10"), t(4, 2, "10"));
    }

    @Test
    void manyUsersAreFullySettledWithAtMostNMinusOneTransfers() {
        Random random = new Random(2024);
        for (int run = 0; run < 200; run++) {
            int n = 2 + random.nextInt(30);
            Map<Long, BigDecimal> net = randomZeroSumBalances(random, n);

            List<Transfer> transfers = DebtSimplifier.simplify(net);

            long nonZero = net.values().stream().filter(v -> v.signum() != 0).count();
            assertThat(transfers.size()).isLessThanOrEqualTo((int) Math.max(0, nonZero - 1));
            Map<Long, BigDecimal> after = new HashMap<>(net);
            for (Transfer tr : transfers) {
                assertThat(tr.amount().signum()).isPositive();
                assertThat(tr.fromUserId()).isNotEqualTo(tr.toUserId());
                after.merge(tr.fromUserId(), tr.amount(), BigDecimal::add);
                after.merge(tr.toUserId(), tr.amount().negate(), BigDecimal::add);
            }
            after.values().forEach(v -> assertThat(v.signum()).isZero());
        }
    }

    @Test
    void isDeterministic() {
        Map<Long, BigDecimal> net = randomZeroSumBalances(new Random(1), 15);
        assertThat(DebtSimplifier.simplify(net)).isEqualTo(DebtSimplifier.simplify(new HashMap<>(net)));
    }

    @Test
    void rejectsBalancesThatDoNotSumToZero() {
        assertThatThrownBy(() -> DebtSimplifier.simplify(Map.of(1L, m("10"), 2L, m("-9.99"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to zero");
    }

    private static Map<Long, BigDecimal> randomZeroSumBalances(Random random, int n) {
        Map<Long, BigDecimal> net = new HashMap<>();
        BigDecimal running = BigDecimal.ZERO;
        for (long id = 1; id < n; id++) {
            BigDecimal v = BigDecimal.valueOf(random.nextInt(200_001) - 100_000, 2);
            net.put(id, v);
            running = running.add(v);
        }
        net.put((long) n, running.negate());
        return net;
    }
}
