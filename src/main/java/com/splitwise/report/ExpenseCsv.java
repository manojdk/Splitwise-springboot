package com.splitwise.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Writes expenses as CSV (RFC 4180), one row per expense and one column per person with their share, the way
 * Splitwise exports. Pure function.
 *
 * <p>Safe to open in Excel or Google Sheets:
 * <ul>
 *   <li>fields with a comma, quote or line break are quoted, and quotes are doubled;</li>
 *   <li><b>formula injection</b>: a text field starting with {@code = + - @}, a tab or a carriage return would be
 *       run as a formula by spreadsheet apps (a classic attack: {@code =HYPERLINK(...)} in an expense name), so
 *       such fields are prefixed with an apostrophe;</li>
 *   <li>the output starts with a UTF-8 byte order mark, so Excel shows non-English names correctly.</li>
 * </ul>
 */
public final class ExpenseCsv {

    static final String BOM = "﻿";
    private static final String LINE_END = "\r\n";

    private ExpenseCsv() {
    }

    /**
     * @param lines expenses, in the order they should appear
     * @param names display name per user id (everyone who appears in the lines)
     */
    public static String write(List<ReportLine> lines, Map<Long, String> names) {
        TreeSet<Long> people = new TreeSet<>(Comparator.comparing((Long id) -> names.getOrDefault(id, ""))
                .thenComparing(Comparator.naturalOrder()));
        for (ReportLine line : lines) {
            people.add(line.payerId());
            people.addAll(line.shares().keySet());
        }
        List<Long> columns = new ArrayList<>(people);

        StringBuilder csv = new StringBuilder(BOM);
        List<String> header = new ArrayList<>(List.of("Date", "Description", "Category", "Amount", "Currency",
                "Paid by", "Split"));
        columns.forEach(id -> header.add(columnName(id, names, columns)));
        appendRow(csv, header);

        for (ReportLine line : lines) {
            List<String> row = new ArrayList<>(List.of(line.date().toString(), line.description(),
                    line.category().name(), money(line.amount()), line.currency(),
                    names.getOrDefault(line.payerId(), "#" + line.payerId()), line.splitType()));
            columns.forEach(id -> row.add(money(line.shares().getOrDefault(id, BigDecimal.ZERO))));
            appendRow(csv, row);
        }
        return csv.toString();
    }

    /** A person's column; two people with the same name are told apart by their id. */
    private static String columnName(Long id, Map<Long, String> names, Collection<Long> columns) {
        String name = names.getOrDefault(id, "#" + id);
        long sameName = columns.stream().filter(other -> Objects.equals(names.get(other), names.get(id))).count();
        return sameName > 1 ? name + " (#" + id + ")" : name;
    }

    private static void appendRow(StringBuilder csv, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(fields.get(i)));
        }
        csv.append(LINE_END);
    }

    static String escape(String value) {
        String field = value == null ? "" : value;
        if (!field.isEmpty() && "=+-@\t\r".indexOf(field.charAt(0)) >= 0) {
            field = "'" + field;
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            field = "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }
}
