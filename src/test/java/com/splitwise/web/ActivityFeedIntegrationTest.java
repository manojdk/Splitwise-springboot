package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The activity feed: what gets recorded, what each person can see, and that it always matches the data.
 */
class ActivityFeedIntegrationTest extends ApiTestSupport {

    @Test
    void theGroupFeedTellsTheWholeStoryNewestFirst() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        long id = equalExpense(alice, groupId, alice, "300.00", alice, bob, carol).get("id").asLong();
        updateExpense(bob, id, updateBody(0, alice, "400.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isOk());
        call(bob, delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        call(alice, post("/api/expenses/" + id + "/restore")).andExpect(status().isOk());

        JsonNode feed = read(call(carol, get("/api/groups/" + groupId + "/activity")).andExpect(status().isOk()));

        assertThat(types(feed)).containsExactly("EXPENSE_RESTORED", "EXPENSE_DELETED", "EXPENSE_UPDATED",
                "EXPENSE_ADDED", "MEMBER_ADDED", "MEMBER_ADDED", "GROUP_CREATED");
        JsonNode edit = feed.at("/content/2");
        assertThat(edit.get("summary").asText()).isEqualTo("Bob updated \"Dinner (edited)\"");
        assertThat(edit.get("actorName").asText()).isEqualTo("Bob");
        assertThat(edit.get("expenseId").asLong()).isEqualTo(id);
        assertThat(texts(edit.get("details"))).containsExactly(
                "Description: \"Dinner\" -> \"Dinner (edited)\"",
                "Amount: 300.00 -> 400.00",
                "Alice's share: 100.00 -> 200.00",
                "Bob's share: 100.00 -> 200.00",
                "Carol removed from the split (was 100.00)");
        assertThat(feed.at("/content/6/summary").asText()).startsWith("Alice created the group");
    }

    @Test
    void anExpenseHasItsOwnHistoryOldestFirstEvenWhenDeleted() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session outsider = user("Mallory");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();
        updateExpense(alice, id, updateBody(0, alice, "120.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isOk());
        call(alice, delete("/api/expenses/" + id)).andExpect(status().isNoContent());

        call(bob, get("/api/expenses/" + id + "/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("EXPENSE_ADDED"))
                .andExpect(jsonPath("$[0].summary").value("Alice added \"Dinner\" (INR 100.00)"))
                .andExpect(jsonPath("$[1].type").value("EXPENSE_UPDATED"))
                .andExpect(jsonPath("$[2].type").value("EXPENSE_DELETED"));
        call(outsider, get("/api/expenses/" + id + "/activity")).andExpect(status().isForbidden());
    }

    @Test
    void settlementsAndMembershipChangesAreRecorded() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        equalExpense(alice, groupId, alice, "100.00", alice, bob);
        long settlementId = read(settle(bob, groupId, bob, alice, "50.00")).get("id").asLong();
        call(alice, delete("/api/settlements/" + settlementId)).andExpect(status().isNoContent());
        call(alice, post("/api/settlements/" + settlementId + "/restore")).andExpect(status().isOk());
        call(alice, put("/api/groups/" + groupId).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Flat 4B")))).andExpect(status().isOk());
        removeMember(bob, groupId, bob).andExpect(status().isOk());

        JsonNode feed = read(call(alice, get("/api/groups/" + groupId + "/activity")));

        List<String> summaries = summaries(feed);
        assertThat(summaries.get(0)).isEqualTo("Bob left \"Flat 4B\"");
        assertThat(summaries.get(1)).startsWith("Alice renamed the group \"Group ").endsWith("\" to \"Flat 4B\"");
        assertThat(summaries.subList(2, 5)).containsExactly(
                "Alice restored a payment: Bob paid Alice INR 50.00",
                "Alice deleted a payment: Bob paid Alice INR 50.00",
                "Bob paid Alice INR 50.00");
    }

    @Test
    void aFormerMemberKeepsSeeingWhatInvolvedThemButNotTheRestOfTheGroup() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        equalExpense(alice, groupId, alice, "90.00", alice, carol);       // involves Carol
        settle(carol, groupId, carol, alice, "45.00").andExpect(status().isCreated());
        removeMember(carol, groupId, carol).andExpect(status().isOk());
        equalExpense(alice, groupId, alice, "40.00", alice, bob);         // after Carol left, not hers

        JsonNode carolsFeed = read(call(carol, get("/api/users/me/activity")).andExpect(status().isOk()));

        assertThat(summaries(carolsFeed)).contains("Carol left \"" + groupName(carolsFeed) + "\"",
                "Carol paid Alice INR 45.00", "Alice added \"Dinner\" (INR 90.00)");
        assertThat(summaries(carolsFeed)).doesNotContain("Alice added \"Dinner\" (INR 40.00)");
        call(carol, get("/api/groups/" + groupId + "/activity")).andExpect(status().isForbidden());
    }

    @Test
    void nonGroupActivityIsPrivateToThePeopleInvolved() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session outsider = user("Mallory");
        addExpense(alice, expenseBody(null, alice, "20.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated());

        assertThat(summaries(read(call(bob, get("/api/users/me/activity")))))
                .containsExactly("Alice added \"Dinner\" (INR 20.00)");
        call(outsider, get("/api/users/me/activity")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aFailedChangeLeavesNoTrace() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();

        updateExpense(alice, id, updateBody(0, alice, "100.00", "EXACT", Share.of(alice, "1.00"), Share.of(bob, "1.00")))
                .andExpect(status().isBadRequest());
        updateExpense(alice, id, updateBody(7, alice, "100.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isConflict());
        call(alice, post("/api/expenses/" + id + "/restore")).andExpect(status().isConflict());

        call(alice, get("/api/expenses/" + id + "/activity")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void theFeedIsPagedAndPageParametersAreValidated() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        for (int i = 0; i < 4; i++) {
            equalExpense(alice, groupId, alice, "10.00", alice, bob);
        }
        // 1 group created + 1 member added + 4 expenses = 6 entries

        call(bob, get("/api/groups/" + groupId + "/activity").param("size", "4"))
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
        call(bob, get("/api/groups/" + groupId + "/activity").param("size", "4").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[1].type").value("GROUP_CREATED"))
                .andExpect(jsonPath("$.last").value(true));
        call(bob, get("/api/users/me/activity").param("size", "0")).andExpect(status().isBadRequest());
        call(bob, get("/api/users/me/activity").param("size", "101")).andExpect(status().isBadRequest());
        call(bob, get("/api/users/me/activity").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void deletingAGroupLeavesOneEntryForItsFormerMembers() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);

        call(alice, delete("/api/groups/" + groupId)).andExpect(status().isNoContent());

        JsonNode bobsFeed = read(call(bob, get("/api/users/me/activity")));
        assertThat(types(bobsFeed)).containsExactly("GROUP_DELETED");
        assertThat(bobsFeed.at("/content/0/groupId").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> types(JsonNode page) {
        return texts(page.get("content"), "type");
    }

    private static List<String> summaries(JsonNode page) {
        return texts(page.get("content"), "summary");
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(field).asText()));
        return values;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    /** The random name the test helper gave the group, read back from its GROUP_CREATED or rename entry. */
    private static String groupName(JsonNode page) {
        for (JsonNode entry : page.get("content")) {
            String summary = entry.get("summary").asText();
            if (entry.get("type").asText().equals("GROUP_CREATED")) {
                return summary.substring(summary.indexOf('"') + 1, summary.lastIndexOf('"'));
            }
            if (entry.get("type").asText().equals("GROUP_RENAMED")) {
                return summary.substring(summary.indexOf('"') + 1, summary.indexOf("\" to"));
            }
            if (entry.get("type").asText().equals("MEMBER_LEFT")) {
                return summary.substring(summary.indexOf('"') + 1, summary.lastIndexOf('"'));
            }
        }
        throw new IllegalStateException("No group name in feed");
    }
}
