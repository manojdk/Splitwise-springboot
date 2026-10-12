package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The friends screen ("you owe / owes you", per group) and settling up with a friend.
 *
 * <p>Scenario used by most tests:
 * <ul>
 *   <li>Flat (Alice, Bob): Alice pays 100, split equally: Bob owes Alice 50.</li>
 *   <li>Trip (Alice, Bob, Carol): Bob pays 90, split equally: Alice and Carol each owe Bob 30.</li>
 *   <li>No group: Alice pays a 40 cab with Bob, split equally: Bob owes Alice 20.</li>
 * </ul>
 * So Bob owes Alice 50 - 30 + 20 = 40 overall, and Carol owes Bob 30.
 */
class FriendBalanceIntegrationTest extends ApiTestSupport {

    private record World(Session alice, Session bob, Session carol, long flat, long trip) {
    }

    private World world() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long flat = group(alice, bob);
        long trip = group(alice, bob, carol);
        equalExpense(alice, flat, alice, "100.00", alice, bob);
        equalExpense(bob, trip, bob, "90.00", alice, bob, carol);
        addExpense(alice, expenseBody(null, alice, "40.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated());
        return new World(alice, bob, carol, flat, trip);
    }

    @Test
    void theFriendsScreenShowsWhoOwesWhomPerGroupWithTotals() throws Exception {
        World w = world();

        call(w.alice(), get("/api/users/me/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.youAreOwed").value(40.00))
                .andExpect(jsonPath("$.youOwe").value(0.00))
                .andExpect(jsonPath("$.net").value(40.00))
                .andExpect(jsonPath("$.friends", hasSize(1)))
                .andExpect(jsonPath("$.friends[0].userId").value(w.bob().id()))
                .andExpect(jsonPath("$.friends[0].name").value("Bob"))
                .andExpect(jsonPath("$.friends[0].balance").value(40.00))
                .andExpect(jsonPath("$.friends[0].breakdown", hasSize(3)));

        // Bob: owes Alice 40, is owed 30 by Carol; largest balance first
        call(w.bob(), get("/api/users/me/friends"))
                .andExpect(jsonPath("$.youAreOwed").value(30.00))
                .andExpect(jsonPath("$.youOwe").value(40.00))
                .andExpect(jsonPath("$.net").value(-10.00))
                .andExpect(jsonPath("$.friends[0].userId").value(w.alice().id()))
                .andExpect(jsonPath("$.friends[0].balance").value(-40.00))
                .andExpect(jsonPath("$.friends[1].userId").value(w.carol().id()))
                .andExpect(jsonPath("$.friends[1].balance").value(30.00));

        // The friends total always equals the overall balance
        assertThat(myOverallBalance(w.alice())).isEqualByComparingTo("40.00");
        assertThat(myOverallBalance(w.bob())).isEqualByComparingTo("-10.00");
    }

    @Test
    void theBreakdownNamesEachGroupAndTheNonGroupPart() throws Exception {
        World w = world();

        call(w.alice(), get("/api/users/me/friends/" + w.bob().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(40.00))
                .andExpect(jsonPath("$.breakdown[?(@.groupId == " + w.flat() + ")].amount").value(50.00))
                .andExpect(jsonPath("$.breakdown[?(@.groupId == " + w.trip() + ")].amount").value(-30.00))
                .andExpect(jsonPath("$.breakdown[?(@.groupId == " + w.trip() + ")].groupName").isNotEmpty())
                .andExpect(jsonPath("$.breakdown[?(@.groupId == null)].amount").value(20.00));

        // Mirror image from Bob's side
        call(w.bob(), get("/api/users/me/friends/" + w.alice().id()))
                .andExpect(jsonPath("$.balance").value(-40.00));
        // Alice and Carol share a group but owe each other nothing there (the group's debts are simplified)
        call(w.alice(), get("/api/users/me/friends/" + w.carol().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.breakdown", hasSize(0)));
    }

    @Test
    void strangersYourselfAndUnknownUsersAreRejected() throws Exception {
        World w = world();
        Session stranger = user("Stranger");

        call(w.alice(), get("/api/users/me/friends/" + stranger.id())).andExpect(status().isNotFound());
        call(w.alice(), get("/api/users/me/friends/999999999")).andExpect(status().isNotFound());
        call(w.alice(), get("/api/users/me/friends/" + w.alice().id())).andExpect(status().isBadRequest());
        call(w.alice(), post("/api/users/me/friends/" + stranger.id() + "/settle-up")).andExpect(status().isNotFound());
        call(stranger, get("/api/users/me/friends"))
                .andExpect(jsonPath("$.friends", hasSize(0)))
                .andExpect(jsonPath("$.net").value(0.00));
    }

    @Test
    void settlingUpRecordsOnePaymentPerGroupAndClearsEverything() throws Exception {
        World w = world();

        JsonNode result = read(call(w.alice(), post("/api/users/me/friends/" + w.bob().id() + "/settle-up"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settlements", hasSize(3)))
                .andExpect(jsonPath("$.balance.balance").value(0.00))
                .andExpect(jsonPath("$.balance.breakdown", hasSize(0))));
        // Each bucket is paid in the direction of its own debt
        assertThat(result.at("/settlements").findValuesAsText("fromUserId"))
                .containsExactlyInAnyOrder(String.valueOf(w.bob().id()), String.valueOf(w.alice().id()),
                        String.valueOf(w.bob().id()));

        // The groups' own balances are right too: the flat is square, in the trip only Carol still owes Bob
        assertThat(balances(w.alice(), w.flat())).containsEntry(w.alice().id(), money("0.00"))
                .containsEntry(w.bob().id(), money("0.00"));
        assertThat(balances(w.alice(), w.trip())).containsEntry(w.alice().id(), money("0.00"))
                .containsEntry(w.bob().id(), money("30.00"))
                .containsEntry(w.carol().id(), money("-30.00"));
        assertThat(myOverallBalance(w.alice())).isEqualByComparingTo("0.00");

        call(w.alice(), post("/api/users/me/friends/" + w.bob().id() + "/settle-up")).andExpect(status().isConflict());
        call(w.bob(), get("/api/users/me/friends"))
                .andExpect(jsonPath("$.friends", hasSize(1)))
                .andExpect(jsonPath("$.friends[0].userId").value(w.carol().id()));
    }

    @Test
    void deletedExpensesDoNotCount() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);
        long id = equalExpense(alice, flat, alice, "100.00", alice, bob).get("id").asLong();

        call(bob, delete("/api/expenses/" + id)).andExpect(status().isNoContent());

        call(alice, get("/api/users/me/friends")).andExpect(jsonPath("$.friends", hasSize(0)));
        call(alice, post("/api/users/me/friends/" + bob.id() + "/settle-up")).andExpect(status().isConflict());
    }
}
