package com.splitwise.split;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.splitwise.domain.SplitType;
import com.splitwise.exception.InvalidSplitException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden cases for the frontend's split calculator: random inputs run through the real strategies, with the
 * result (or the fact that the input was refused) written to {@code frontend/src/lib/split-golden.json}. The
 * frontend test replays every case, so the live preview in the expense form is proven to give the same paise
 * as the API.
 *
 * <p>The generator is seeded, so the file only changes when a split rule changes; then this test fails until
 * the file is regenerated: {@code ./mvnw test -Dtest=SplitGoldenCasesTest -Dsplit.update=true}.
 */
class SplitGoldenCasesTest {

    private static final Path FILE = Path.of("frontend", "src", "lib", "split-golden.json");
    private static final int CASES = 400;

    private final Map<SplitType, SplitStrategy> strategies = Map.of(
            SplitType.EQUAL, new EqualSplitStrategy(),
            SplitType.EXACT, new ExactSplitStrategy(),
            SplitType.PERCENTAGE, new PercentageSplitStrategy(),
            SplitType.SHARES, new SharesSplitStrategy(),
            SplitType.ADJUSTMENT, new AdjustmentSplitStrategy());

    @Test
    void goldenCasesMatchTheSplitRules() throws Exception {
        Random random = new Random(20261012);
        List<Map<String, Object>> cases = new ArrayList<>();
        int refused = 0;
        for (int i = 0; i < CASES; i++) {
            SplitType type = SplitType.values()[i % SplitType.values().length];
            Map<String, Object> c = generate(random, type);
            if (c.get("result") == null) {
                refused++;
            }
            cases.add(c);
        }
        // Both outcomes must be well represented, or the replay proves little
        assertThat(refused).isBetween(CASES / 8, CASES / 2);
        EDGE_CASES.forEach(e -> cases.add(run(e.type(), e.total(), e.ids(), e.values())));

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String text = mapper.writeValueAsString(cases).replace("\r\n", "\n") + "\n";
        if (Boolean.getBoolean("split.update")) {
            Files.writeString(FILE, text, StandardCharsets.UTF_8);
            return;
        }
        assertThat(FILE).as("missing %s; run with -Dsplit.update=true", FILE).exists();
        assertThat(Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("%s is out of date; run with -Dsplit.update=true", FILE)
                .isEqualTo(text);
    }

    private record Edge(SplitType type, String total, List<Long> ids, List<String> values) {
    }

    /** Boundaries that random inputs rarely hit exactly. */
    private static final List<Edge> EDGE_CASES = List.of(
            new Edge(SplitType.PERCENTAGE, "100.00", List.of(1L, 2L), List.of("50.00005", "49.99995")),
            new Edge(SplitType.PERCENTAGE, "100.00", List.of(1L, 2L, 3L), List.of("33.3333", "33.3333", "33.3334")),
            new Edge(SplitType.PERCENTAGE, "10.00", List.of(1L, 2L), List.of("150", "-50")),
            new Edge(SplitType.PERCENTAGE, "10.00", List.of(1L, 2L), List.of("100", "0")),
            new Edge(SplitType.SHARES, "0.05", List.of(3L, 1L, 2L), Arrays.asList("0", "0", "1")),
            new Edge(SplitType.SHARES, "10.00", List.of(1L, 2L), List.of("1000000", "1")),
            new Edge(SplitType.SHARES, "10.00", List.of(1L, 2L), List.of("1000000.01", "1")),
            new Edge(SplitType.SHARES, "10.00", List.of(1L, 2L), List.of("1.125", "0.875")),
            new Edge(SplitType.SHARES, "10.00", List.of(1L, 2L), Arrays.asList("1", null)),
            new Edge(SplitType.EXACT, "10.00", List.of(1L, 2L), List.of("11.00", "-1.00")),
            new Edge(SplitType.EXACT, "10.00", List.of(1L, 2L), List.of("10.00", "0")),
            new Edge(SplitType.ADJUSTMENT, "10.00", List.of(1L, 2L), Arrays.asList("10.00", null)),
            new Edge(SplitType.ADJUSTMENT, "10.00", List.of(1L, 2L), Arrays.asList("-1.00", null)),
            new Edge(SplitType.ADJUSTMENT, "0.01", List.of(5L, 4L, 3L), Arrays.asList(null, null, null)),
            new Edge(SplitType.EQUAL, "0.01", List.of(9L, 2L, 5L), Arrays.asList(null, null, null)),
            new Edge(SplitType.EQUAL, "100.00", List.of(3L, 1L, 2L), Arrays.asList(null, null, null)),
            new Edge(SplitType.EQUAL, "100.00", List.of(1L, 1L), Arrays.asList(null, null)),
            new Edge(SplitType.EQUAL, "10.10", List.of(), List.of()),
            new Edge(SplitType.EQUAL, "-5.00", List.of(1L), Arrays.asList((String) null)),
            new Edge(SplitType.EQUAL, "999999999.99", List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                    Arrays.asList(null, null, null, null, null, null, null)));

    private Map<String, Object> generate(Random random, SplitType type) {
        int people = 1 + random.nextInt(6);
        List<Long> ids = new ArrayList<>();
        while (ids.size() < people) {
            long id = 1 + random.nextInt(60);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        long totalCents = switch (random.nextInt(4)) {
            case 0 -> 1 + random.nextInt(7);               // a few paise: rounding edge cases
            case 1 -> 1 + random.nextInt(10_000);
            case 2 -> 1 + random.nextInt(10_000_000);
            default -> 1 + (long) (random.nextDouble() * 99_999_999_999L);
        };
        String total = BigDecimal.valueOf(totalCents, 2).toPlainString();
        if (random.nextInt(25) == 0) {
            total = random.nextBoolean() ? "0.00" : total + "5"; // zero, or three decimals
        }

        List<String> values = switch (type) {
            case EQUAL -> Collections.nCopies(people, null);
            case EXACT -> exactValues(random, totalCents, people);
            case PERCENTAGE -> percentValues(random, people);
            case SHARES -> shareValues(random, people);
            case ADJUSTMENT -> adjustmentValues(random, totalCents, people);
        };

        return run(type, total, ids, values);
    }

    /** Runs the real strategy; the result is null when the API would refuse the input. */
    private Map<String, Object> run(SplitType type, String total, List<Long> ids, List<String> values) {
        List<SplitParticipant> participants = new ArrayList<>();
        List<Map<String, Object>> shown = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String value = values.get(i);
            participants.add(new SplitParticipant(ids.get(i), value == null ? null : new BigDecimal(value)));
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("userId", ids.get(i));
            p.put("value", value);
            shown.add(p);
        }

        Map<String, Object> result = null;
        try {
            Map<String, Object> owed = new LinkedHashMap<>();
            strategies.get(type).split(new BigDecimal(total), participants)
                    .forEach((userId, amount) -> owed.put(String.valueOf(userId), amount.toPlainString()));
            result = owed;
        } catch (InvalidSplitException refusedInput) {
            // result stays null: the API answers 400 for this input
        }

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("type", type.name());
        c.put("total", total);
        c.put("participants", shown);
        c.put("result", result);
        return c;
    }

    /** Usually an exact partition of the total; sometimes off by a cent, or with three decimals. */
    private static List<String> exactValues(Random random, long totalCents, int people) {
        List<Long> parts = partition(random, totalCents, people);
        List<String> values = new ArrayList<>(parts.stream().map(c -> BigDecimal.valueOf(c, 2).toPlainString()).toList());
        switch (random.nextInt(6)) {
            case 0 -> values.set(0, BigDecimal.valueOf(parts.get(0) + 1, 2).toPlainString());
            case 1 -> values.set(0, values.get(0) + "1");
            default -> {
            }
        }
        return values;
    }

    /** Percentages summing to 100 (whole, or up to 4 decimals); sometimes a wrong sum or too many decimals. */
    private static List<String> percentValues(Random random, int people) {
        boolean decimals = random.nextBoolean();
        List<Long> parts = partition(random, decimals ? 1_000_000 : 100, people);
        List<String> values = new ArrayList<>(parts.stream()
                .map(p -> decimals ? BigDecimal.valueOf(p, 4).stripTrailingZeros().toPlainString() : String.valueOf(p))
                .toList());
        switch (random.nextInt(6)) {
            case 0 -> values.set(0, new BigDecimal(values.get(0)).add(BigDecimal.ONE).toPlainString());
            case 1 -> values.set(0, values.get(0) + (values.get(0).contains(".") ? "00001" : ".00001"));
            default -> {
            }
        }
        return values;
    }

    /** 0 to 5 shares, sometimes with decimals; sometimes all zero or three decimals. */
    private static List<String> shareValues(Random random, int people) {
        List<String> values = new ArrayList<>();
        int mode = random.nextInt(8);
        for (int i = 0; i < people; i++) {
            if (mode == 0) {
                values.add("0");
            } else if (random.nextInt(4) == 0) {
                values.add(BigDecimal.valueOf(random.nextInt(500), 2).toPlainString());
            } else {
                values.add(String.valueOf(random.nextInt(6)));
            }
        }
        if (mode == 1) {
            values.set(0, "1.125");
        }
        return values;
    }

    /** Empty (null) or a part of the total; sometimes adding up to more than the total. */
    private static List<String> adjustmentValues(Random random, long totalCents, int people) {
        boolean tooMuch = random.nextInt(6) == 0;
        List<String> values = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            if (!tooMuch && random.nextBoolean()) {
                values.add(null);
            } else {
                long cents = tooMuch ? totalCents : random.nextLong(Math.max(1, totalCents / people) + 1);
                values.add(BigDecimal.valueOf(cents, 2).toPlainString());
            }
        }
        return values;
    }

    /** Splits a whole number into {@code parts} non-negative random pieces that add up to it exactly. */
    private static List<Long> partition(Random random, long whole, int parts) {
        List<Long> cuts = new ArrayList<>();
        for (int i = 0; i < parts - 1; i++) {
            cuts.add(random.nextLong(whole + 1));
        }
        cuts.add(0L);
        cuts.add(whole);
        Collections.sort(cuts);
        List<Long> pieces = new ArrayList<>();
        for (int i = 1; i < cuts.size(); i++) {
            pieces.add(cuts.get(i) - cuts.get(i - 1));
        }
        return pieces;
    }
}
