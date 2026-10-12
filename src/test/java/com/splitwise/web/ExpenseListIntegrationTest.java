package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paging and filtering of expense and settlement lists.
 */
class ExpenseListIntegrationTest extends ApiTestSupport {

    private record World(Session alice, Session bob, long groupId) {
    }

    /**
     * Five expenses in one group:
     * Rent (Alice, RENT, 2026-09-01), Groceries (Bob, GROCERIES, 2026-09-05), Pizza 50% off (Bob, FOOD, 2026-09-20),
     * Wifi_bill (Alice, UTILITIES, 2026-10-01), Groceries again (Alice, GROCERIES, 2026-10-03).
     */
    private World world() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        World w = new World(alice, bob, groupId);
        expense(w, alice, "Rent", "RENT", "2026-09-01");
        expense(w, bob, "Groceries", "GROCERIES", "2026-09-05");
        expense(w, bob, "Pizza 50% off", "FOOD_AND_DRINK", "2026-09-20");
        expense(w, alice, "Wifi_bill", "UTILITIES", "2026-10-01");
        expense(w, alice, "Groceries again", "GROCERIES", "2026-10-03");
        return w;
    }

    /** An expense paid by {@code payer}, split equally between Alice and Bob. */
    private void expense(World w, Session payer, String description, String category, String date) throws Exception {
        Session caller = payer;
        Map<String, Object> body = expenseBody(w.groupId(), payer, "100.00", "EQUAL", Share.of(w.alice()),
                Share.of(w.bob()));
        body.put("description", description);
        body.put("category", category);
        body.put("date", date);
        addExpense(caller, body).andExpect(status().isCreated());
    }

    private List<String> descriptions(ResultActions result) throws Exception {
        JsonNode page = read(result.andExpect(status().isOk()));
        List<String> names = new ArrayList<>();
        page.get("content").forEach(e -> names.add(e.get("description").asText()));
        return names;
    }

    private ResultActions list(World w, String... params) throws Exception {
        var request = get("/api/groups/" + w.groupId() + "/expenses");
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return call(w.alice(), request);
    }

    @Test
    void listsAreNewestFirstAndPagedWithoutOverlap() throws Exception {
        World w = world();

        assertThat(descriptions(list(w, "size", "2"))).containsExactly("Groceries again", "Wifi_bill");
        assertThat(descriptions(list(w, "size", "2", "page", "1"))).containsExactly("Pizza 50% off", "Groceries");
        list(w, "size", "2", "page", "2")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void filtersNarrowTheListAndCombine() throws Exception {
        World w = world();

        assertThat(descriptions(list(w, "category", "GROCERIES"))).containsExactly("Groceries again", "Groceries");
        assertThat(descriptions(list(w, "from", "2026-09-05", "to", "2026-09-30")))
                .containsExactly("Pizza 50% off", "Groceries");
        assertThat(descriptions(list(w, "paidBy", String.valueOf(w.bob().id()))))
                .containsExactly("Pizza 50% off", "Groceries");
        assertThat(descriptions(list(w, "category", "GROCERIES", "paidBy", String.valueOf(w.alice().id()))))
                .containsExactly("Groceries again");
        assertThat(descriptions(list(w, "q", "GROCER"))).as("case-insensitive")
                .containsExactly("Groceries again", "Groceries");
    }

    @Test
    void searchTextIsTakenLiterallyNotAsAWildcard() throws Exception {
        World w = world();

        // % and _ are LIKE wildcards; a user typing them means the characters themselves
        assertThat(descriptions(list(w, "q", "50%"))).containsExactly("Pizza 50% off");
        assertThat(descriptions(list(w, "q", "i_b"))).containsExactly("Wifi_bill");
        assertThat(descriptions(list(w, "q", "%"))).containsExactly("Pizza 50% off");
    }

    @Test
    void myExpensesAcrossGroupsUseTheSameFilters() throws Exception {
        World w = world();
        addExpense(w.bob(), expenseBody(null, w.bob(), "40.00", "EQUAL", Share.of(w.bob()), Share.of(w.alice())))
                .andExpect(status().isCreated());

        call(w.alice(), get("/api/users/me/expenses").param("q", "dinner"))
                .andExpect(jsonPath("$.totalElements").value(1));
        call(w.alice(), get("/api/users/me/expenses").param("category", "GROCERIES"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void settlementListsArePagedAndFilteredByDate() throws Exception {
        World w = world();
        for (String date : List.of("2026-09-10", "2026-09-25", "2026-10-02")) {
            Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of("fromUserId", w.bob().id(),
                    "toUserId", w.alice().id(), "amount", 10, "groupId", w.groupId()));
            body.put("date", date);
            call(w.bob(), postJson("/api/settlements", body)).andExpect(status().isCreated());
        }

        call(w.alice(), get("/api/groups/" + w.groupId() + "/settlements").param("size", "2"))
                .andExpect(jsonPath("$.content[0].date").value("2026-10-02"))
                .andExpect(jsonPath("$.totalElements").value(3));
        call(w.bob(), get("/api/users/me/settlements").param("from", "2026-09-20").param("to", "2026-09-30"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].date").value("2026-09-25"));
    }

    @Test
    void badParametersAreA400() throws Exception {
        World w = world();

        list(w, "size", "0").andExpect(status().isBadRequest());
        list(w, "size", "101").andExpect(status().isBadRequest());
        list(w, "page", "-1").andExpect(status().isBadRequest());
        list(w, "from", "2026-10-02", "to", "2026-10-01").andExpect(status().isBadRequest());
        list(w, "category", "CRYPTO").andExpect(status().isBadRequest());
        list(w, "q", "x".repeat(101)).andExpect(status().isBadRequest());
        call(w.alice(), get("/api/users/me/settlements").param("from", "2026-10-02").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }
}
