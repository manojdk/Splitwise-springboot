package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SHARES and ADJUSTMENT splits, and expense categories and notes, through the API.
 */
class SplitTypesAndCategoryIntegrationTest extends ApiTestSupport {

    @Test
    void sharesSplitAHotelRoomTwoToOne() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);

        addExpense(alice, expenseBody(groupId, alice, "300.00", "SHARES", Share.of(alice, "2"), Share.of(bob, "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitType").value("SHARES"))
                .andExpect(jsonPath("$.shares[?(@.userId == " + bob.id() + ")].amount").value(100.00));

        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("-100.00"));
    }

    @Test
    void adjustmentChargesTheExtraToOnePersonAndSplitsTheRest() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);

        // 330 dinner; Carol had a 30 dessert: 300 split three ways, plus 30 for Carol
        addExpense(alice, expenseBody(groupId, alice, "330.00", "ADJUSTMENT",
                Share.of(alice), Share.of(bob), Share.of(carol, "30")))
                .andExpect(status().isCreated());

        assertThat(balances(alice, groupId))
                .containsEntry(bob.id(), money("-100.00"))
                .containsEntry(carol.id(), money("-130.00"))
                .containsEntry(alice.id(), money("230.00"));
    }

    @Test
    void invalidSharesOrAdjustmentsAreA400AndNothingIsSaved() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);

        addExpense(alice, expenseBody(groupId, alice, "10.00", "SHARES", Share.of(alice, "0"), Share.of(bob, "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("more than 0 shares")));
        addExpense(alice, expenseBody(groupId, alice, "10.00", "ADJUSTMENT", Share.of(alice, "8"), Share.of(bob, "5")))
                .andExpect(status().isBadRequest());
        addExpense(alice, expenseBody(groupId, alice, "10.00", "SPLIT_BY_VIBES", Share.of(alice)))
                .andExpect(status().isBadRequest());

        call(alice, get("/api/groups/" + groupId + "/expenses")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void categoryAndNotesAreSavedReturnedAndEditable() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        Map<String, Object> body = expenseBody(groupId, alice, "1200.00", "EQUAL", Share.of(alice), Share.of(bob));
        body.put("category", "RENT");
        body.put("notes", "  October rent, flat 4B  ");

        long id = read(addExpense(alice, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("RENT"))
                .andExpect(jsonPath("$.notes").value("October rent, flat 4B")))
                .get("id").asLong();

        Map<String, Object> edit = updateBody(0, alice, "1200.00", "EQUAL", Share.of(alice), Share.of(bob));
        edit.put("category", "UTILITIES");
        updateExpense(alice, id, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("UTILITIES"))
                // full replacement: no notes in the edit means the notes are cleared
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void anExpenseWithoutACategoryIsGeneralAndBadCategoriesOrLongNotesAreRejected() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);

        addExpense(alice, expenseBody(groupId, alice, "10.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(jsonPath("$.category").value("GENERAL"));

        Map<String, Object> badCategory = expenseBody(groupId, alice, "10.00", "EQUAL", Share.of(alice));
        badCategory.put("category", "CRYPTO");
        addExpense(alice, badCategory).andExpect(status().isBadRequest());

        Map<String, Object> longNotes = expenseBody(groupId, alice, "10.00", "EQUAL", Share.of(alice));
        longNotes.put("notes", "x".repeat(1001));
        addExpense(alice, longNotes).andExpect(status().isBadRequest());
    }
}
