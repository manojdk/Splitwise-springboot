package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group spending report and CSV export over HTTP.
 */
class ReportIntegrationTest extends ApiTestSupport {

    private long expense(Session caller, long groupId, Session payer, String amount, String category, String date,
                         Share... shares) throws Exception {
        Map<String, Object> body = expenseBody(groupId, payer, amount, "EQUAL", shares);
        body.put("category", category);
        body.put("date", date);
        return read(addExpense(caller, body).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void theReportSummarisesSpendingByCategoryAndMember() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        expense(alice, groupId, alice, "1000.00", "RENT", "2026-09-01", Share.of(alice), Share.of(bob));
        expense(bob, groupId, bob, "300.00", "GROCERIES", "2026-09-10", Share.of(alice), Share.of(bob));
        expense(bob, groupId, bob, "200.00", "GROCERIES", "2026-10-02", Share.of(bob));
        long deleted = expense(alice, groupId, alice, "999.00", "TRAVEL", "2026-09-15", Share.of(alice));
        call(alice, delete("/api/expenses/" + deleted)).andExpect(status().isNoContent());

        call(alice, get("/api/groups/" + groupId + "/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseCount").value(3))
                .andExpect(jsonPath("$.totalSpent").value(1500.00))
                .andExpect(jsonPath("$.byCategory[0].category").value("RENT"))
                .andExpect(jsonPath("$.byCategory[1].category").value("GROCERIES"))
                .andExpect(jsonPath("$.byCategory[1].total").value(500.00))
                .andExpect(jsonPath("$.byCategory[1].count").value(2))
                .andExpect(jsonPath("$.byMember[?(@.userId == " + bob.id() + ")].paid").value(500.00))
                .andExpect(jsonPath("$.byMember[?(@.userId == " + bob.id() + ")].share").value(850.00))
                .andExpect(jsonPath("$.byMember[?(@.userId == " + bob.id() + ")].net").value(-350.00))
                .andExpect(jsonPath("$.byMember[?(@.userId == " + bob.id() + ")].name").value("Bob"));

        // September only
        call(alice, get("/api/groups/" + groupId + "/report").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(jsonPath("$.expenseCount").value(2))
                .andExpect(jsonPath("$.totalSpent").value(1300.00))
                .andExpect(jsonPath("$.from").value("2026-09-01"));
    }

    @Test
    void anEmptyRangeAndBadRequestsAreHandled() throws Exception {
        Session alice = user("Alice");
        Session outsider = user("Mallory");
        long groupId = group(alice);

        call(alice, get("/api/groups/" + groupId + "/report").param("from", "2030-01-01"))
                .andExpect(jsonPath("$.totalSpent").value(0.00))
                .andExpect(jsonPath("$.byCategory", hasSize(0)));
        call(alice, get("/api/groups/" + groupId + "/report").param("from", "2026-10-02").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
        call(alice, get("/api/groups/" + groupId + "/report").param("from", "yesterday"))
                .andExpect(status().isBadRequest());
        call(outsider, get("/api/groups/" + groupId + "/report")).andExpect(status().isForbidden());
        call(outsider, get("/api/groups/" + groupId + "/expenses.csv")).andExpect(status().isForbidden());
    }

    @Test
    void csvExportIsADownloadSafeToOpenInASpreadsheet() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        Map<String, Object> body = expenseBody(groupId, alice, "100.00", "EQUAL", Share.of(alice), Share.of(bob));
        body.put("description", "=HYPERLINK(\"http://evil.example\",\"refund\")");
        body.put("date", LocalDate.of(2026, 10, 1).toString());
        addExpense(alice, body).andExpect(status().isCreated());

        byte[] bytes = call(bob, get("/api/groups/" + groupId + "/expenses.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"group-" + groupId + "-expenses.csv\"")))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(bytes, StandardCharsets.UTF_8);

        String[] rows = csv.substring(1).split("\r\n");
        assertThat(rows[0]).isEqualTo("Date,Description,Category,Amount,Currency,Paid by,Split,Alice,Bob");
        assertThat(rows[1]).startsWith("2026-10-01,\"'=HYPERLINK(").endsWith(",GENERAL,100.00,INR,Alice,EQUAL,50.00,50.00");
    }
}
