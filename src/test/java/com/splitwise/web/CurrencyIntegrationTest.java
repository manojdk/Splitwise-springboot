package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One currency per group; any currency outside groups; balances kept per currency, never added together.
 */
class CurrencyIntegrationTest extends ApiTestSupport {

    private long euroGroup(Session owner, Session... members) throws Exception {
        long groupId = read(call(owner, postJson("/api/groups", Map.of("name", "Paris", "currency", "EUR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))).get("id").asLong();
        for (Session member : members) {
            call(owner, postJson("/api/groups/" + groupId + "/members", Map.of("email", member.email())))
                    .andExpect(status().isOk());
        }
        return groupId;
    }

    @Test
    void everythingInAGroupIsInTheGroupsCurrency() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long paris = euroGroup(alice, bob);

        addExpense(alice, expenseBody(paris, alice, "90.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"));
        Map<String, Object> inRupees = expenseBody(paris, alice, "90.00", "EQUAL", Share.of(alice), Share.of(bob));
        inRupees.put("currency", "INR");
        addExpense(alice, inRupees)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("uses EUR")));
        Map<String, Object> payment = new java.util.LinkedHashMap<>(Map.of("fromUserId", bob.id(),
                "toUserId", alice.id(), "amount", 10, "groupId", paris, "currency", "USD"));
        call(bob, postJson("/api/settlements", payment)).andExpect(status().isBadRequest());

        call(alice, get("/api/groups/" + paris + "/balances"))
                .andExpect(jsonPath("$[0].currency").value("EUR"));
        call(alice, get("/api/groups/" + paris + "/debts/simplified"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].amount").value(45.00));
        call(alice, get("/api/groups/" + paris + "/report")).andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void recurringExpensesFollowTheGroupCurrencyToo() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long paris = euroGroup(alice, bob);
        Map<String, Object> rent = expenseBody(paris, alice, "1000.00", "EQUAL", Share.of(alice), Share.of(bob));
        rent.put("frequency", "MONTHLY");
        rent.put("startDate", LocalDate.now(clock).toString());

        call(alice, postJson("/api/recurring-expenses", rent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"));
        rent.put("currency", "GBP");
        call(alice, postJson("/api/recurring-expenses", rent)).andExpect(status().isBadRequest());
    }

    @Test
    void invalidCurrenciesAreRejected() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");

        call(alice, postJson("/api/groups", Map.of("name", "X", "currency", "XYZ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unknown currency")));
        call(alice, postJson("/api/groups", Map.of("name", "X", "currency", "eur"))).andExpect(status().isBadRequest());
        Map<String, Object> body = expenseBody(null, alice, "10.00", "EQUAL", Share.of(alice), Share.of(bob));
        body.put("currency", "ABC");
        addExpense(alice, body).andExpect(status().isBadRequest());
    }

    @Test
    void balancesAreKeptPerCurrencyAndNeverAddedTogether() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long paris = euroGroup(alice, bob);
        long goa = group(alice, bob); // INR
        equalExpense(alice, paris, alice, "100.00", alice, bob);   // Bob owes Alice EUR 50
        equalExpense(alice, goa, alice, "1000.00", alice, bob);    // Bob owes Alice INR 500
        Map<String, Object> taxi = expenseBody(null, bob, "20.00", "EQUAL", Share.of(alice), Share.of(bob));
        taxi.put("currency", "USD");                               // Alice owes Bob USD 10
        addExpense(bob, taxi).andExpect(status().isCreated());

        call(alice, get("/api/users/me/balance"))
                .andExpect(jsonPath("$.balances", hasSize(3)))
                .andExpect(jsonPath("$.balances[?(@.currency == 'EUR')].amount").value(50.00))
                .andExpect(jsonPath("$.balances[?(@.currency == 'INR')].amount").value(500.00))
                .andExpect(jsonPath("$.balances[?(@.currency == 'USD')].amount").value(-10.00));

        call(alice, get("/api/users/me/friends"))
                .andExpect(jsonPath("$.totals", hasSize(3)))
                .andExpect(jsonPath("$.totals[?(@.currency == 'USD')].youOwe").value(10.00))
                .andExpect(jsonPath("$.friends", hasSize(1)))
                .andExpect(jsonPath("$.friends[0].balances", hasSize(3)))
                .andExpect(jsonPath("$.friends[0].breakdown[?(@.groupId == " + paris + ")].currency").value("EUR"));

        // One settle-up: a payment per group in its currency, plus the USD one outside groups
        call(alice, post("/api/users/me/friends/" + bob.id() + "/settle-up"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settlements", hasSize(3)))
                .andExpect(jsonPath("$.settlements[?(@.currency == 'EUR')].amount").value(50.00))
                .andExpect(jsonPath("$.balance.balances", hasSize(0)));
        call(alice, get("/api/users/me/balance")).andExpect(jsonPath("$.balances", hasSize(0)));
        assertThat(balances(alice, paris)).containsEntry(bob.id(), money("0.00"));
    }
}
