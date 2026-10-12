package com.splitwise.report;

import com.splitwise.domain.Category;
import com.splitwise.domain.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Spending summary of a set of expenses. Pure function.
 *
 * <p>Invariants (property-tested): the category totals, the members' "paid" and the members' "share" each add up
 * to the total spent, and the members' nets add up to zero.
 *
 * @param byCategory largest first, categories with no spending left out
 * @param byMember   by user id
 */
public record GroupReport(int expenseCount, BigDecimal totalSpent, List<CategoryTotal> byCategory,
                          List<MemberTotal> byMember) {

    public record CategoryTotal(Category category, BigDecimal total, int count) {
    }

    /**
     * @param paid  what the member paid for
     * @param share what the member's own share of the expenses came to (what they "consumed")
     * @param net   paid - share: positive = paid for others
     */
    public record MemberTotal(long userId, BigDecimal paid, BigDecimal share, BigDecimal net) {
    }

    public static GroupReport of(Collection<ReportLine> lines) {
        BigDecimal total = Money.ZERO;
        Map<Category, BigDecimal> categoryTotals = new EnumMap<>(Category.class);
        Map<Category, Integer> categoryCounts = new EnumMap<>(Category.class);
        Map<Long, BigDecimal> paid = new TreeMap<>();
        Map<Long, BigDecimal> share = new TreeMap<>();
        for (ReportLine line : lines) {
            total = total.add(line.amount());
            categoryTotals.merge(line.category(), line.amount(), BigDecimal::add);
            categoryCounts.merge(line.category(), 1, Integer::sum);
            paid.merge(line.payerId(), line.amount(), BigDecimal::add);
            share.putIfAbsent(line.payerId(), Money.ZERO);
            line.shares().forEach((userId, amount) -> {
                share.merge(userId, amount, BigDecimal::add);
                paid.putIfAbsent(userId, Money.ZERO);
            });
        }
        List<CategoryTotal> byCategory = new ArrayList<>();
        categoryTotals.forEach((category, sum) ->
                byCategory.add(new CategoryTotal(category, Money.of(sum), categoryCounts.get(category))));
        byCategory.sort(Comparator.comparing(CategoryTotal::total).reversed()
                .thenComparing(CategoryTotal::category));

        List<MemberTotal> byMember = new ArrayList<>();
        paid.forEach((userId, paidAmount) -> {
            BigDecimal shareAmount = share.getOrDefault(userId, Money.ZERO);
            byMember.add(new MemberTotal(userId, Money.of(paidAmount), Money.of(shareAmount),
                    Money.of(paidAmount.subtract(shareAmount))));
        });
        return new GroupReport(lines.size(), Money.of(total), byCategory, byMember);
    }
}
