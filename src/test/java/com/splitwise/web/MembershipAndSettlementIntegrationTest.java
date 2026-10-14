package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Leaving a group, removing members, and undoing settlements.
 */
class MembershipAndSettlementIntegrationTest extends ApiTestSupport {

    // ------------------------------------------------------------------ leave / remove

    @Test
    void aMemberWithAZeroBalanceCanLeave() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        equalExpense(alice, groupId, alice, "100.00", alice, bob); // Carol is not involved

        removeMember(carol, groupId, carol)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(2)));

        assertThat(balances(alice, groupId)).doesNotContainKey(carol.id());
        call(carol, get("/api/groups/" + groupId)).andExpect(status().isForbidden());
        call(carol, get("/api/groups")).andExpect(jsonPath("$[?(@.id == " + groupId + ")]").isEmpty());
        call(alice, get("/api/groups/" + groupId + "/debts/simplified")).andExpect(status().isOk());
    }

    @Test
    void aMemberWhoStillOwesMoneyCannotLeaveUntilSettled() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        equalExpense(alice, groupId, alice, "100.00", alice, bob);

        removeMember(bob, groupId, bob)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("-50.00")));

        settle(bob, groupId, bob, alice, "50.00").andExpect(status().isCreated());
        removeMember(bob, groupId, bob).andExpect(status().isOk());
    }

    @Test
    void someoneWhoIsOwedMoneyCannotLeaveEither() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        equalExpense(carol, groupId, carol, "90.00", alice, bob, carol);

        removeMember(carol, groupId, carol).andExpect(status().isConflict());
    }

    @Test
    void onlyTheCreatorRemovesOthersAndTheCreatorCannotLeave() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        Session outsider = user("Mallory");
        long groupId = group(alice, bob, carol);

        removeMember(bob, groupId, carol).andExpect(status().isForbidden());
        removeMember(alice, groupId, alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("creator")));
        removeMember(alice, groupId, outsider).andExpect(status().isNotFound());
        removeMember(outsider, groupId, bob).andExpect(status().isForbidden());

        removeMember(alice, groupId, carol).andExpect(status().isOk());
    }

    @Test
    void aFormerMemberCanBeAddedBackAndHistoryKeepsTheirName() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        equalExpense(alice, groupId, alice, "100.00", alice, bob);
        settle(bob, groupId, bob, alice, "50.00").andExpect(status().isCreated());
        removeMember(bob, groupId, bob).andExpect(status().isOk());

        // The old expense still names Bob, and the group's numbers still work
        call(alice, get("/api/groups/" + groupId + "/expenses"))
                .andExpect(jsonPath("$.content[0].shares[?(@.userId == " + bob.id() + ")].name").value("Bob"));
        assertThat(balances(alice, groupId)).containsEntry(alice.id(), money("0.00"));

        call(alice, postJson("/api/groups/" + groupId + "/members", java.util.Map.of("email", bob.email())))
                .andExpect(status().isOk());
        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("0.00"));
    }

    @Test
    void messagesNamePeopleTheCallerKnowsButNeverWhoAnArbitraryIdBelongsTo() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session stranger = user("Zed");
        long groupId = group(alice, bob);
        equalExpense(alice, groupId, alice, "100.00", alice, bob);

        removeMember(alice, groupId, bob)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bob still has a balance of INR -50.00 in this group; "
                        + "settle up first"));
        removeMember(bob, groupId, bob)
                .andExpect(jsonPath("$.message").value("You still have a balance of INR -50.00 in this group; "
                        + "settle up first"));
        call(stranger, get("/api/groups/" + groupId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of this group"));
        // The stranger's id in a group expense: refused without saying who that id is
        addExpense(alice, expenseBody(groupId, alice, "10.00", "EQUAL", Share.of(alice), Share.of(stranger)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(containsString("Zed"))))
                .andExpect(jsonPath("$.message", containsString("not a member of this group")));
        settle(alice, groupId, alice, stranger, "5.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(containsString("Zed"))));

        settle(bob, groupId, bob, alice, "50.00").andExpect(status().isCreated());
        call(alice, post("/api/users/me/friends/" + bob.id() + "/settle-up"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You and Bob are already settled up"));
    }

    @Test
    void creatingAGroupCannotPullInOtherUsersByIdOrRevealTheirEmails() throws Exception {
        Session alice = user("Alice");
        Session mallory = user("Mallory");

        long groupId = read(call(mallory, postJson("/api/groups",
                java.util.Map.of("name", "Harvest", "memberIds", java.util.List.of(alice.id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].id").value(mallory.id())))
                .get("id").asLong();

        call(alice, get("/api/groups")).andExpect(jsonPath("$[?(@.id == " + groupId + ")]").isEmpty());
    }

    @Test
    void aStrangerCannotPutADebtOnSomeoneOutsideGroupsOrFindOutWhoHasAnAccount() throws Exception {
        Session alice = user("Alice");
        Session mallory = user("Mallory");
        String refused = "Outside groups you can only split or settle with people you share a group with";

        settle(mallory, null, mallory, alice, "5000.00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString(refused)));
        addExpense(mallory, expenseBody(null, mallory, "100.00", "EQUAL", Share.of(mallory), Share.of(alice)))
                .andExpect(status().isForbidden());
        // An id with no account gets the same answer, so ids cannot be probed
        call(mallory, postJson("/api/settlements", java.util.Map.of("fromUserId", mallory.id(),
                "toUserId", 999_999_999L, "amount", 5)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString(refused)));
        assertThat(myOverallBalance(alice)).isEqualByComparingTo("0");

        // Once they share a group, splitting outside it is allowed, and stays allowed after leaving
        long groupId = group(alice, mallory);
        settle(mallory, null, mallory, alice, "10.00").andExpect(status().isCreated());
        removeMember(mallory, groupId, mallory).andExpect(status().isOk());
        settle(alice, null, alice, mallory, "10.00").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ after someone leaves

    // Someone who left can neither see the group's balances nor record a payment in it, so nothing may change
    // what they owe or are owed there.

    @Test
    void deletingAPaymentOfSomeoneWhoLeftIsRefusedSoTheyAreNotLeftWithAnInvisibleDebt() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        equalExpense(alice, groupId, alice, "100.00", alice, bob);
        long paymentId = read(settle(bob, groupId, bob, alice, "50.00").andExpect(status().isCreated()))
                .get("id").asLong();
        removeMember(bob, groupId, bob).andExpect(status().isOk());

        call(alice, delete("/api/settlements/" + paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Bob, who has left the group")));

        assertThat(myOverallBalance(bob)).isEqualByComparingTo("0");
        assertThat(balances(alice, groupId)).containsEntry(alice.id(), money("0.00"));
    }

    @Test
    void deletingAnExpenseThatMovesWhatSomeoneWhoLeftOwesIsRefused() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        long expenseId = equalExpense(carol, groupId, carol, "90.00", alice, bob, carol).get("id").asLong();
        settle(bob, groupId, bob, carol, "30.00").andExpect(status().isCreated());
        removeMember(bob, groupId, bob).andExpect(status().isOk());

        call(alice, delete("/api/expenses/" + expenseId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Bob, who has left the group")));
        assertThat(myOverallBalance(bob)).isEqualByComparingTo("0");
    }

    @Test
    void anExpenseInvolvingSomeoneWhoLeftCanStillBeRenamedButNotResplit() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        JsonNode expense = equalExpense(carol, groupId, carol, "90.00", alice, bob, carol);
        settle(bob, groupId, bob, carol, "30.00").andExpect(status().isCreated());
        removeMember(bob, groupId, bob).andExpect(status().isOk());

        // Taking Bob out of the split would give him money back in a group he cannot see
        updateExpense(alice, expense.get("id").asLong(), updateBody(expense.get("version").asLong(), carol, "90.00",
                "EQUAL", Share.of(alice), Share.of(carol)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Bob, who has left the group")));

        // Same people, same amounts, new description: nobody's balance moves
        updateExpense(alice, expense.get("id").asLong(), updateBody(expense.get("version").asLong(), carol, "90.00",
                "EQUAL", Share.of(alice), Share.of(bob), Share.of(carol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Dinner (edited)"));
        assertThat(myOverallBalance(bob)).isEqualByComparingTo("0");
    }

    @Test
    void expensesThatDoNotInvolveSomeoneWhoLeftCanStillBeDeletedAndRestored() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        long expenseId = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();
        removeMember(carol, groupId, carol).andExpect(status().isOk());

        call(alice, delete("/api/expenses/" + expenseId)).andExpect(status().isNoContent());
        call(alice, post("/api/expenses/" + expenseId + "/restore")).andExpect(status().isOk());
    }

    @Test
    void aDeletedPaymentOfSomeoneWhoLeftCannotBeRestoredAndTheMessageNamesThem() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        equalExpense(alice, groupId, alice, "100.00", alice, bob);
        long paymentId = read(settle(bob, groupId, bob, alice, "50.00").andExpect(status().isCreated()))
                .get("id").asLong();
        long refundId = read(settle(alice, groupId, alice, bob, "50.00").andExpect(status().isCreated()))
                .get("id").asLong();
        call(alice, delete("/api/settlements/" + refundId)).andExpect(status().isNoContent());
        removeMember(bob, groupId, bob).andExpect(status().isOk());

        call(alice, post("/api/settlements/" + refundId + "/restore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Bob, who has left the group")));
        assertThat(paymentId).isPositive();
    }

    // ------------------------------------------------------------------ settlements

    @Test
    void aSettlementRecordedByMistakeCanBeUndoneAndRestored() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        equalExpense(alice, groupId, alice, "100.00", alice, bob);
        long settlementId = read(settle(bob, groupId, bob, alice, "50.00").andExpect(status().isCreated()))
                .get("id").asLong();
        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("0.00"));

        call(alice, delete("/api/settlements/" + settlementId)).andExpect(status().isNoContent());

        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("-50.00"));
        call(bob, get("/api/groups/" + groupId + "/settlements")).andExpect(jsonPath("$.content", hasSize(0)));
        call(bob, get("/api/users/me/settlements")).andExpect(jsonPath("$.content", hasSize(0)));
        call(bob, get("/api/settlements/" + settlementId)).andExpect(jsonPath("$.deleted").value(true));

        call(bob, post("/api/settlements/" + settlementId + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(false));
        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("0.00"));
        call(bob, post("/api/settlements/" + settlementId + "/restore")).andExpect(status().isConflict());
    }

    @Test
    void settlementAccessFollowsTheSameRulesAsExpenses() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        Session outsider = user("Mallory");
        long groupId = group(alice, bob, carol);
        long groupSettlement = read(settle(bob, groupId, bob, alice, "10.00")).get("id").asLong();
        long privateSettlement = read(settle(bob, null, bob, alice, "5.00")).get("id").asLong();

        call(carol, get("/api/settlements/" + groupSettlement)).andExpect(status().isOk()); // group member
        call(outsider, delete("/api/settlements/" + groupSettlement)).andExpect(status().isForbidden());
        call(carol, get("/api/settlements/" + privateSettlement)).andExpect(status().isForbidden());
        call(alice, delete("/api/settlements/" + privateSettlement)).andExpect(status().isNoContent());
        call(alice, get("/api/settlements/999999999")).andExpect(status().isNotFound());
    }
}
