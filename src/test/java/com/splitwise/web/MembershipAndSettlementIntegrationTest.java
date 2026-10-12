package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
