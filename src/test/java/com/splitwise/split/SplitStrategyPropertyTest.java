package com.splitwise.split;

import com.splitwise.domain.SplitType;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for every split type: jqwik generates random totals (0.01 to 10,000,000.00), 1-8
 * participants and valid values for each type, and checks the contract every strategy promises.
 */
class SplitStrategyPropertyTest {

    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final SplitStrategyResolver resolver = new SplitStrategyResolver(List.of(new EqualSplitStrategy(),
            new ExactSplitStrategy(), new PercentageSplitStrategy(), new SharesSplitStrategy(),
            new AdjustmentSplitStrategy()));

    /** One generated split: the type, the total and valid participant values for that type. */
    record Case(SplitType type, BigDecimal total, List<SplitParticipant> participants) {
    }

    // ------------------------------------------------------------------ the contract, for all types

    @Property(tries = 1000)
    void partsAlwaysAddUpToTheTotalExactly(@ForAll("cases") Case c) {
        Map<Long, BigDecimal> owed = split(c);

        assertThat(owed.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(c.total());
    }

    @Property(tries = 1000)
    void partsAreWholeCentsNeverNegativeAndOnePerParticipant(@ForAll("cases") Case c) {
        Map<Long, BigDecimal> owed = split(c);

        assertThat(owed.keySet()).containsExactlyInAnyOrderElementsOf(
                c.participants().stream().map(SplitParticipant::userId).toList());
        assertThat(owed.values()).allSatisfy(amount -> {
            assertThat(amount.scale()).isEqualTo(2);
            assertThat(amount.signum()).isNotNegative();
        });
    }

    @Property(tries = 500)
    void theOrderOfTheParticipantsDoesNotMatter(@ForAll("cases") Case c, @ForAll long seed) {
        List<SplitParticipant> shuffled = new ArrayList<>(c.participants());
        Collections.shuffle(shuffled, new Random(seed));

        assertThat(resolver.forType(c.type()).split(c.total(), shuffled)).isEqualTo(split(c));
    }

    // ------------------------------------------------------------------ fairness, per type

    @Property(tries = 1000)
    void eachPartIsWithinOneCentOfItsExactValue(@ForAll("cases") Case c) {
        Map<Long, BigDecimal> owed = split(c);
        switch (c.type()) {
            case EXACT -> c.participants().forEach(p ->
                    assertThat(owed.get(p.userId())).isEqualByComparingTo(p.value()));
            case EQUAL -> {
                BigDecimal max = owed.values().stream().max(BigDecimal::compareTo).orElseThrow();
                BigDecimal min = owed.values().stream().min(BigDecimal::compareTo).orElseThrow();
                assertThat(max.subtract(min)).isLessThanOrEqualTo(CENT);
            }
            case PERCENTAGE, SHARES -> {
                BigDecimal weightSum = c.participants().stream().map(SplitParticipant::value)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                for (SplitParticipant p : c.participants()) {
                    BigDecimal exact = c.total().multiply(p.value()).divide(weightSum, MathContext.DECIMAL128);
                    assertThat(owed.get(p.userId()).subtract(exact).abs()).isLessThan(CENT);
                    if (p.value().signum() == 0) {
                        assertThat(owed.get(p.userId())).isEqualByComparingTo("0");
                    }
                }
            }
            case ADJUSTMENT -> {
                // Remove each person's adjustment: what is left is an equal split, so within one cent
                List<BigDecimal> equalParts = c.participants().stream()
                        .map(p -> owed.get(p.userId()).subtract(p.value() == null ? BigDecimal.ZERO : p.value()))
                        .toList();
                BigDecimal max = equalParts.stream().max(BigDecimal::compareTo).orElseThrow();
                BigDecimal min = equalParts.stream().min(BigDecimal::compareTo).orElseThrow();
                assertThat(max.subtract(min)).isLessThanOrEqualTo(CENT);
                assertThat(min.signum()).isNotNegative();
            }
        }
    }

    private Map<Long, BigDecimal> split(Case c) {
        return resolver.forType(c.type()).split(c.total(), c.participants());
    }

    // ------------------------------------------------------------------ generators

    @Provide
    Arbitrary<Case> cases() {
        Arbitrary<Long> totalCents = Arbitraries.oneOf(
                Arbitraries.longs().between(1, 1_000),               // tiny amounts: rounding is hardest
                Arbitraries.longs().between(1, 1_000_000_000L));     // up to 10,000,000.00
        Arbitrary<Integer> size = Arbitraries.integers().between(1, 8);
        Arbitrary<SplitType> type = Arbitraries.of(SplitType.class);
        Arbitrary<Long> seed = Arbitraries.longs();
        return net.jqwik.api.Combinators.combine(type, totalCents, size, seed)
                .as((t, cents, n, s) -> build(t, cents, n, new Random(s)));
    }

    /** Builds valid values for the type from a seeded random source (so jqwik can replay and shrink). */
    private static Case build(SplitType type, long totalCents, int n, Random random) {
        BigDecimal total = BigDecimal.valueOf(totalCents, 2);
        List<Long> ids = new ArrayList<>();
        while (ids.size() < n) {
            long id = 1 + random.nextInt(1000);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        List<SplitParticipant> participants = new ArrayList<>();
        switch (type) {
            case EQUAL -> ids.forEach(id -> participants.add(new SplitParticipant(id, null)));
            case EXACT -> {
                long[] parts = cut(totalCents, n, random);
                for (int i = 0; i < n; i++) {
                    participants.add(new SplitParticipant(ids.get(i), BigDecimal.valueOf(parts[i], 2)));
                }
            }
            case PERCENTAGE -> {
                long[] parts = cut(1_000_000, n, random); // 100% in units of 0.0001%
                for (int i = 0; i < n; i++) {
                    participants.add(new SplitParticipant(ids.get(i), BigDecimal.valueOf(parts[i], 4)));
                }
            }
            case SHARES -> {
                for (int i = 0; i < n; i++) {
                    long units = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(100_000);
                    participants.add(new SplitParticipant(ids.get(i), BigDecimal.valueOf(units, random.nextInt(3))));
                }
                if (participants.stream().allMatch(p -> p.value().signum() == 0)) {
                    participants.set(0, new SplitParticipant(ids.get(0), BigDecimal.ONE));
                }
            }
            case ADJUSTMENT -> {
                long adjustable = (long) (totalCents * random.nextDouble());
                long[] parts = cut(adjustable, n, random);
                for (int i = 0; i < n; i++) {
                    BigDecimal value = random.nextInt(3) == 0 && parts[i] == 0 ? null : BigDecimal.valueOf(parts[i], 2);
                    participants.add(new SplitParticipant(ids.get(i), value));
                }
            }
        }
        return new Case(type, total, participants);
    }

    /** Splits {@code amount} into {@code n} non-negative parts that add up to it exactly. */
    private static long[] cut(long amount, int n, Random random) {
        long[] points = new long[n + 1];
        points[n] = amount;
        for (int i = 1; i < n; i++) {
            points[i] = amount == 0 ? 0 : (long) (random.nextDouble() * (amount + 1));
        }
        java.util.Arrays.sort(points, 1, n);
        long[] parts = new long[n];
        for (int i = 0; i < n; i++) {
            parts[i] = points[i + 1] - points[i];
        }
        return parts;
    }
}
