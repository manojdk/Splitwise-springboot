package com.splitwise.report;

import com.splitwise.domain.Category;
import com.splitwise.report.GroupReport.MemberTotal;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report's numbers must always reconcile: categories, payments and shares each add up to the total spent,
 * and members' nets add up to zero.
 */
class GroupReportPropertyTest {

    @Property(tries = 500)
    void everyBreakdownAddsUpToTheTotal(@ForAll("lines") List<ReportLine> lines) {
        GroupReport report = GroupReport.of(lines);

        BigDecimal byCategory = report.byCategory().stream().map(GroupReport.CategoryTotal::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = report.byMember().stream().map(MemberTotal::paid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal share = report.byMember().stream().map(MemberTotal::share).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = report.byMember().stream().map(MemberTotal::net).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(byCategory).isEqualByComparingTo(report.totalSpent());
        assertThat(paid).isEqualByComparingTo(report.totalSpent());
        assertThat(share).isEqualByComparingTo(report.totalSpent());
        assertThat(net).isEqualByComparingTo("0");
        assertThat(report.byCategory().stream().mapToInt(GroupReport.CategoryTotal::count).sum())
                .isEqualTo(lines.size());
    }

    @Property(tries = 300)
    void categoriesAreLargestFirst(@ForAll("lines") List<ReportLine> lines) {
        List<GroupReport.CategoryTotal> categories = GroupReport.of(lines).byCategory();
        for (int i = 1; i < categories.size(); i++) {
            assertThat(categories.get(i - 1).total()).isGreaterThanOrEqualTo(categories.get(i).total());
        }
    }

    @Test
    void anEmptyPeriodIsAllZero() {
        GroupReport report = GroupReport.of(List.of());

        assertThat(report.totalSpent()).isEqualByComparingTo("0");
        assertThat(report.byCategory()).isEmpty();
        assertThat(report.byMember()).isEmpty();
    }

    @Test
    void aPayerWhoIsNotInTheSplitPaidEverythingAndConsumedNothing() {
        ReportLine taxi = new ReportLine(LocalDate.of(2026, 10, 1), "Taxi", Category.TRANSPORT,
                new BigDecimal("60.00"), "INR", 1L, "EQUAL",
                new TreeMap<>(Map.of(2L, new BigDecimal("30.00"), 3L, new BigDecimal("30.00"))));

        List<MemberTotal> members = GroupReport.of(List.of(taxi)).byMember();

        assertThat(members).containsExactly(
                new MemberTotal(1L, new BigDecimal("60.00"), new BigDecimal("0.00"), new BigDecimal("60.00")),
                new MemberTotal(2L, new BigDecimal("0.00"), new BigDecimal("30.00"), new BigDecimal("-30.00")),
                new MemberTotal(3L, new BigDecimal("0.00"), new BigDecimal("30.00"), new BigDecimal("-30.00")));
    }

    @Provide
    Arbitrary<List<ReportLine>> lines() {
        Arbitrary<Category> category = Arbitraries.of(Category.class);
        Arbitrary<Long> payer = Arbitraries.longs().between(1, 5);
        Arbitrary<List<Long>> shareCents = Arbitraries.longs().between(0, 500_000).list().ofSize(5);
        Arbitrary<ReportLine> line = Combinators.combine(category, payer, shareCents).as((c, p, cents) -> {
            Map<Long, BigDecimal> shares = new TreeMap<>();
            BigDecimal total = BigDecimal.ZERO.setScale(2);
            for (int i = 0; i < cents.size(); i++) {
                if (cents.get(i) > 0) {
                    BigDecimal amount = BigDecimal.valueOf(cents.get(i), 2);
                    shares.put((long) i + 1, amount);
                    total = total.add(amount);
                }
            }
            if (shares.isEmpty()) {
                shares.put(1L, new BigDecimal("0.01"));
                total = new BigDecimal("0.01");
            }
            return new ReportLine(LocalDate.of(2026, 10, 1), "x", c, total, "INR", p, "EXACT", shares);
        });
        return line.list().ofMaxSize(30);
    }
}
