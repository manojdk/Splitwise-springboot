package com.splitwise.service;

import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
import com.splitwise.exception.BadRequestException;
import com.splitwise.report.ExpenseCsv;
import com.splitwise.report.GroupReport;
import com.splitwise.report.ReportLine;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.web.dto.GroupReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Group spending reports and CSV export (members only). The calculation and the CSV format are pure classes in
 * {@code com.splitwise.report}; this service selects the expenses and resolves names.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;

    public ReportService(GroupService groupService, ExpenseRepository expenseRepository) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
    }

    public GroupReportResponse groupReport(Long callerId, Long groupId, LocalDate from, LocalDate to) {
        Group group = groupService.requireMemberGroup(groupId, callerId);
        List<Expense> expenses = expensesInRange(groupId, from, to);
        Map<Long, String> names = names(expenses);
        GroupReport report = GroupReport.of(expenses.stream().map(ReportLine::of).toList());
        return new GroupReportResponse(groupId, group.getName(), from, to, Money.DEFAULT_CURRENCY,
                report.expenseCount(), report.totalSpent(),
                report.byCategory().stream()
                        .map(c -> new GroupReportResponse.CategoryTotal(c.category(), c.total(), c.count()))
                        .toList(),
                report.byMember().stream()
                        .map(m -> new GroupReportResponse.MemberTotal(m.userId(), names.get(m.userId()), m.paid(),
                                m.share(), m.net()))
                        .toList());
    }

    /** The group's expenses in the range as CSV, oldest first. */
    public String groupCsv(Long callerId, Long groupId, LocalDate from, LocalDate to) {
        groupService.requireMemberGroup(groupId, callerId);
        List<Expense> expenses = expensesInRange(groupId, from, to).stream()
                .sorted(Comparator.comparing(Expense::getExpenseDate).thenComparing(Expense::getId))
                .toList();
        return ExpenseCsv.write(expenses.stream().map(ReportLine::of).toList(), names(expenses));
    }

    private List<Expense> expensesInRange(Long groupId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        return expenseRepository.findByGroupIdAndDeletedAtIsNullOrderByExpenseDateDescIdDesc(groupId).stream()
                .filter(e -> from == null || !e.getExpenseDate().isBefore(from))
                .filter(e -> to == null || !e.getExpenseDate().isAfter(to))
                .toList();
    }

    private static Map<Long, String> names(List<Expense> expenses) {
        Map<Long, String> names = new HashMap<>();
        for (Expense e : expenses) {
            names.put(e.getPaidBy().getId(), e.getPaidBy().getName());
            for (ExpenseShare s : e.getShares()) {
                names.put(s.getUser().getId(), s.getUser().getName());
            }
        }
        return names;
    }
}
