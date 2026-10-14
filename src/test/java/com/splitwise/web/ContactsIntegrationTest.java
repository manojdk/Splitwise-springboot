package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who you can split with outside groups, and a friend's page: the expenses you share with one person.
 */
class ContactsIntegrationTest extends ApiTestSupport {

    @Test
    void contactsAreGroupMatesAndPeopleYouHaveSplitWithButNeverStrangers() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        Session dan = user("Dan");
        user("Stranger");
        group(alice, bob, carol);
        // Alice and Dan met in a group, split a cab outside it, and Dan then left the group
        long danGroup = group(alice, dan);
        addExpense(alice, expenseBody(null, alice, "20.00", "EQUAL", Share.of(alice), Share.of(dan)))
                .andExpect(status().isCreated());
        settle(dan, null, dan, alice, "10.00").andExpect(status().isCreated());
        removeMember(dan, danGroup, dan).andExpect(status().isOk());

        call(alice, get("/api/users/me/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Bob", "Carol", "Dan")))
                .andExpect(jsonPath("$[0].email").value(bob.email()));
        call(bob, get("/api/users/me/contacts")).andExpect(jsonPath("$[*].name", contains("Alice", "Carol")));
        call(dan, get("/api/users/me/contacts")).andExpect(jsonPath("$[*].name", contains("Alice")));
    }

    @Test
    void aFriendsPageListsOnlyTheExpensesYouShareWithThem() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        long withBob = equalExpense(alice, groupId, alice, "90.00", alice, bob, carol).get("id").asLong();
        equalExpense(alice, groupId, alice, "30.00", alice, carol);
        long outside = read(addExpense(bob, expenseBody(null, bob, "40.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated())).get("id").asLong();

        call(alice, get("/api/users/me/expenses").param("with", String.valueOf(bob.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) withBob, (int) outside)));
        // Carol cannot see Alice and Bob's cab by asking for it
        call(carol, get("/api/users/me/expenses").param("with", String.valueOf(bob.id())))
                .andExpect(jsonPath("$.content[*].id", contains((int) withBob)));
    }
}
