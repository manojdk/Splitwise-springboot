package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Editing, deleting and restoring expenses, and what that does to balances.
 */
class ExpenseLifecycleIntegrationTest extends ApiTestSupport {

    // ------------------------------------------------------------------ edit

    @Test
    void editingAnExpenseChangesTheBalances() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        JsonNode dinner = equalExpense(alice, groupId, alice, "300.00", alice, bob, carol);
        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("-100.00"))
                .containsEntry(carol.id(), money("-100.00"));

        // Bob (a member, not the creator of the expense) fixes it: it was 400, and Carol was not there
        updateExpense(bob, dinner.get("id").asLong(),
                updateBody(0, alice, "400.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(400.00))
                .andExpect(jsonPath("$.description").value("Dinner (edited)"))
                .andExpect(jsonPath("$.shares", hasSize(2)))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedById").value(bob.id()))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.createdById").value(alice.id()));

        assertThat(balances(alice, groupId))
                .containsEntry(alice.id(), money("200.00"))
                .containsEntry(bob.id(), money("-200.00"))
                .containsEntry(carol.id(), money("0.00"));
    }

    @Test
    void editKeepingTheSameParticipantsUpdatesTheirShares() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();

        // Same two people, new amounts: the share rows are updated in place (the unique (expense, user)
        // constraint would reject a delete-and-reinsert done in the same flush)
        updateExpense(alice, id, updateBody(0, alice, "100.00", "EXACT",
                Share.of(alice, "30.00"), Share.of(bob, "70.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares[?(@.userId == " + bob.id() + ")].amount").value(70.00));

        assertThat(balances(bob, groupId)).containsEntry(bob.id(), money("-70.00"));
    }

    @Test
    void aStaleVersionIsRejectedSoNobodysEditIsLost() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();

        // Both read version 0. Alice saves first...
        updateExpense(alice, id, updateBody(0, alice, "120.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isOk());
        // ...so Bob's save, based on version 0, must not overwrite her change
        updateExpense(bob, id, updateBody(0, alice, "90.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("changed by someone else")));

        call(bob, get("/api/expenses/" + id))
                .andExpect(jsonPath("$.amount").value(120.00))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void anInvalidEditChangesNothing() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session outsider = user("Mallory");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();

        updateExpense(alice, id, updateBody(0, alice, "100.00", "EXACT",
                Share.of(alice, "30.00"), Share.of(bob, "60.00")))
                .andExpect(status().isBadRequest());
        updateExpense(alice, id, updateBody(0, alice, "100.00", "EQUAL", Share.of(alice), Share.of(outsider)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not a member")));
        updateExpense(outsider, id, updateBody(0, alice, "1.00", "EQUAL", Share.of(alice)))
                .andExpect(status().isForbidden());
        updateExpense(alice, 999_999_999, updateBody(0, alice, "1.00", "EQUAL", Share.of(alice)))
                .andExpect(status().isNotFound());
        Map<String, Object> noVersion = updateBody(0, alice, "1.00", "EQUAL", Share.of(alice));
        noVersion.remove("version");
        updateExpense(alice, id, noVersion).andExpect(status().isBadRequest());

        call(alice, get("/api/expenses/" + id))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void youCannotEditYourselfOutOfANonGroupExpense() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long id = read(addExpense(alice, expenseBody(null, alice, "50.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated())).get("id").asLong();

        // Afterwards Alice would have no access to an expense she just changed
        updateExpense(alice, id, updateBody(0, bob, "50.00", "EQUAL", Share.of(bob), Share.of(carol)))
                .andExpect(status().isForbidden());
        updateExpense(carol, id, updateBody(0, alice, "50.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ delete and restore

    @Test
    void deletingRemovesTheExpenseFromListsAndBalancesAndRestoringBringsItBack() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();
        equalExpense(bob, groupId, bob, "40.00", alice, bob);
        assertThat(myOverallBalance(bob)).isEqualByComparingTo("-30.00");

        call(bob, delete("/api/expenses/" + id)).andExpect(status().isNoContent());

        call(alice, get("/api/groups/" + groupId + "/expenses")).andExpect(jsonPath("$.content", hasSize(1)));
        call(alice, get("/api/users/me/expenses")).andExpect(jsonPath("$.content[?(@.id == " + id + ")]").isEmpty());
        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("20.00"));
        assertThat(myOverallBalance(bob)).isEqualByComparingTo("20.00");
        call(alice, get("/api/expenses/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        call(alice, delete("/api/expenses/" + id)).andExpect(status().isNoContent()); // idempotent
        updateExpense(alice, id, updateBody(1, alice, "10.00", "EQUAL", Share.of(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("deleted")));

        call(alice, post("/api/expenses/" + id + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));
        assertThat(myOverallBalance(bob)).isEqualByComparingTo("-30.00");
        call(alice, post("/api/expenses/" + id + "/restore")).andExpect(status().isConflict());
    }

    @Test
    void outsidersCannotDeleteOrRestore() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session outsider = user("Mallory");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();

        call(outsider, delete("/api/expenses/" + id)).andExpect(status().isForbidden());
        call(outsider, post("/api/expenses/" + id + "/restore")).andExpect(status().isForbidden());
        call(alice, delete("/api/expenses/999999999")).andExpect(status().isNotFound());
        call(alice, get("/api/expenses/" + id)).andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void anExpenseCannotBeRestoredOnceSomeoneInItHasLeftTheGroup() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();

        call(alice, delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        removeMember(bob, groupId, bob).andExpect(status().isOk()); // Bob's balance is now 0, he leaves

        call(alice, post("/api/expenses/" + id + "/restore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("no longer a member")));
    }
}
