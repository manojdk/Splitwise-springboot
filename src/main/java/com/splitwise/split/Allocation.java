package com.splitwise.split;

import com.splitwise.domain.Money;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact ways of dividing an amount. Everything is done in whole units of the currency (cents, or whole yen when
 * {@code decimals} is 0) with integer arithmetic, so there is no rounding drift: the parts always add up to the
 * total exactly. Results have the app's scale of 2 either way (34 yen is 34.00).
 */
final class Allocation {

    private Allocation() {
    }

    /**
     * Equal parts. Leftover cents (total mod n) go one each to the first ids in the given order, so
     * 100.00 / 3 = 33.34, 33.33, 33.33 every time.
     */
    static Map<Long, BigDecimal> equal(BigDecimal total, List<Long> userIds, int decimals) {
        BigInteger[] divRem = units(total, decimals).divideAndRemainder(BigInteger.valueOf(userIds.size()));
        int remainder = divRem[1].intValueExact();
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < userIds.size(); i++) {
            BigInteger share = i < remainder ? divRem[0].add(BigInteger.ONE) : divRem[0];
            result.put(userIds.get(i), amount(share, decimals));
        }
        return result;
    }

    /**
     * Parts proportional to the weights, by the largest-remainder method: each part is first rounded down to
     * whole cents, then the cents lost are handed out one each to the largest discarded remainders (ties: first
     * id in the given order). A zero weight never receives a cent.
     *
     * <p>The remainders are compared exactly: with total T cents and weights w(i) summing to W, part i is
     * floor(T * w(i) / W) and its remainder is (T * w(i)) mod W; all remainders share the denominator W, so
     * comparing the numerators is exact. Weights may have decimals; they are scaled to integers first.
     *
     * @param weights non-negative, same order as {@code userIds}, sum greater than zero
     */
    static Map<Long, BigDecimal> proportional(BigDecimal total, List<Long> userIds, List<BigDecimal> weights,
                                              int decimals) {
        int scale = weights.stream().mapToInt(w -> Math.max(0, w.stripTrailingZeros().scale())).max().orElse(0);
        List<BigInteger> intWeights = weights.stream().map(w -> w.movePointRight(scale).toBigIntegerExact()).toList();
        BigInteger weightSum = intWeights.stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger totalCents = units(total, decimals);

        record Part(int index, BigInteger floor, BigInteger remainder) {
        }
        List<Part> parts = new ArrayList<>();
        BigInteger allocated = BigInteger.ZERO;
        for (int i = 0; i < userIds.size(); i++) {
            BigInteger[] divRem = totalCents.multiply(intWeights.get(i)).divideAndRemainder(weightSum);
            parts.add(new Part(i, divRem[0], divRem[1]));
            allocated = allocated.add(divRem[0]);
        }
        int leftover = totalCents.subtract(allocated).intValueExact();
        List<Part> byRemainder = parts.stream()
                .sorted(Comparator.comparing(Part::remainder).reversed().thenComparingInt(Part::index))
                .toList();
        BigInteger[] result = parts.stream().map(Part::floor).toArray(BigInteger[]::new);
        for (int i = 0; i < leftover; i++) {
            Part part = byRemainder.get(i);
            result[part.index()] = result[part.index()].add(BigInteger.ONE);
        }
        Map<Long, BigDecimal> owed = new LinkedHashMap<>();
        for (int i = 0; i < userIds.size(); i++) {
            owed.put(userIds.get(i), amount(result[i], decimals));
        }
        return owed;
    }

    /** The amount in whole units (cents, or yen); the caller has checked it has no finer part. */
    private static BigInteger units(BigDecimal amount, int decimals) {
        return amount.movePointRight(decimals).toBigIntegerExact();
    }

    private static BigDecimal amount(BigInteger units, int decimals) {
        return new BigDecimal(units, decimals).setScale(Money.SCALE);
    }
}
