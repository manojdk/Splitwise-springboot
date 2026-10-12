package com.splitwise.report;

import com.splitwise.domain.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCsvTest {

    private static ReportLine line(String description, long payer, Map<Long, String> shares) {
        Map<Long, BigDecimal> amounts = new TreeMap<>();
        shares.forEach((id, amount) -> amounts.put(id, new BigDecimal(amount)));
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReportLine(LocalDate.of(2026, 10, 1), description, Category.FOOD_AND_DRINK, total, "INR", payer,
                "EXACT", amounts);
    }

    @Test
    void oneRowPerExpenseAndOneColumnPerPersonSortedByName() {
        String csv = ExpenseCsv.write(
                List.of(line("Dinner", 2, Map.of(1L, "30.00", 2L, "70.00"))),
                Map.of(1L, "Zoe", 2L, "Amit"));

        assertThat(csv).startsWith(ExpenseCsv.BOM);
        assertThat(csv.substring(1).split("\r\n")).containsExactly(
                "Date,Description,Category,Amount,Currency,Paid by,Split,Amit,Zoe",
                "2026-10-01,Dinner,FOOD_AND_DRINK,100.00,INR,Amit,EXACT,70.00,30.00");
    }

    @Test
    void someoneNotInAnExpenseGetsZeroInThatRow() {
        String csv = ExpenseCsv.write(List.of(
                        line("Taxi", 1, Map.of(1L, "10.00")),
                        line("Lunch", 2, Map.of(2L, "20.00"))),
                Map.of(1L, "Asha", 2L, "Bina"));

        assertThat(csv.split("\r\n")[1]).endsWith(",10.00,0.00");
        assertThat(csv.split("\r\n")[2]).endsWith(",0.00,20.00");
    }

    @Test
    void commasQuotesAndLineBreaksAreQuoted() {
        assertThat(ExpenseCsv.escape("Pizza, pasta")).isEqualTo("\"Pizza, pasta\"");
        assertThat(ExpenseCsv.escape("The \"good\" place")).isEqualTo("\"The \"\"good\"\" place\"");
        assertThat(ExpenseCsv.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(ExpenseCsv.escape("plain")).isEqualTo("plain");
        assertThat(ExpenseCsv.escape(null)).isEmpty();
    }

    @Test
    void textThatASpreadsheetWouldRunAsAFormulaIsDefused() {
        assertThat(ExpenseCsv.escape("=HYPERLINK(\"http://evil\",\"click\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\",\"\"click\"\")\"");
        assertThat(ExpenseCsv.escape("+91 cab")).isEqualTo("'+91 cab");
        assertThat(ExpenseCsv.escape("-2+3")).isEqualTo("'-2+3");
        assertThat(ExpenseCsv.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(ExpenseCsv.escape("\tcmd")).isEqualTo("'\tcmd");
    }

    @Test
    void twoPeopleWithTheSameNameGetSeparateColumns() {
        String csv = ExpenseCsv.write(List.of(line("Tea", 1, Map.of(1L, "5.00", 2L, "5.00"))),
                Map.of(1L, "Rahul", 2L, "Rahul"));

        assertThat(csv.split("\r\n")[0]).endsWith(",Rahul (#1),Rahul (#2)");
    }
}
