package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.recurring.RecurringExpenseJob;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A stopped or paused recurring expense can be resumed. It carries on from the next date on or after today; the
 * dates missed while it was stopped are skipped, so nobody suddenly finds months of rent added at once.
 */
class RecurringResumeIntegrationTest extends ApiTestSupport {

    @Autowired
    private RecurringExpenseJob job;

    @Test
    void resumingSkipsTheMissedDatesAndCarriesOnFromTheNextOne() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        LocalDate start = LocalDate.now(clock);
        long id = rent(alice, groupId, bob, start, null);
        call(alice, delete("/api/recurring-expenses/" + id)).andExpect(status().isNoContent());

        clock.advance(Duration.ofDays(70)); // two monthly dates missed
        resume(bob, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.stoppedReason").doesNotExist())
                .andExpect(jsonPath("$.nextDate").value(start.plusMonths(3).toString()));
        assertThat(groupExpenses(alice, groupId)).hasSize(1);
        call(alice, get("/api/groups/" + groupId + "/activity"))
                .andExpect(jsonPath("$.content[0].type").value("RECURRING_RESUMED"))
                .andExpect(jsonPath("$.content[0].summary").value("Bob resumed the recurring \"Rent\""));

        clock.setDate(start.plusMonths(3));
        job.runDueNow();
        JsonNode expenses = groupExpenses(alice, groupId);
        assertThat(expenses).hasSize(2);
        assertThat(expenses.get(0).get("date").asText()).isEqualTo(start.plusMonths(3).toString());
    }

    @Test
    void ifTheNextDateIsTodayItsExpenseIsCreatedAtOnce() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        LocalDate start = LocalDate.now(clock);
        long id = rent(alice, groupId, bob, start, null);
        call(alice, delete("/api/recurring-expenses/" + id)).andExpect(status().isNoContent());

        clock.setDate(start.plusMonths(1));
        resume(alice, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDate").value(start.plusMonths(2).toString()));
        assertThat(groupExpenses(alice, groupId)).hasSize(2);
    }

    @Test
    void aTemplatePausedBecauseSomeoneLeftResumesOnlyOnceTheyAreBack() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        long id = rent(alice, groupId, carol, LocalDate.now(clock).plusDays(1), null);
        removeMember(carol, groupId, carol).andExpect(status().isOk());
        clock.advance(Duration.ofDays(1));
        job.runDueNow();
        call(alice, get("/api/recurring-expenses/" + id)).andExpect(jsonPath("$.active").value(false));

        resume(alice, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Carol is no longer a member of the group; add them back first, or set up a new recurring"
                                + " expense without them"));

        call(alice, postJson("/api/groups/" + groupId + "/members", Map.of("email", carol.email())))
                .andExpect(status().isOk());
        resume(alice, id).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(groupExpenses(alice, groupId)).hasSize(1); // today's date was due, so it is created
    }

    @Test
    void aTemplateWhoseEndDateHasPassedCannotResume() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        LocalDate start = LocalDate.now(clock);
        long id = rent(alice, groupId, bob, start, start.plusDays(40));
        call(alice, delete("/api/recurring-expenses/" + id)).andExpect(status().isNoContent());

        clock.advance(Duration.ofDays(45));
        resume(alice, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("It ended on " + start.plusDays(40)
                        + "; set up a new recurring expense instead"));
        call(alice, get("/api/recurring-expenses/" + id)).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void onlyThePeopleItBelongsToCanResumeItAndResumingARunningOneChangesNothing() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session mallory = user("Mallory");
        long groupId = group(alice, bob);
        LocalDate start = LocalDate.now(clock);
        long id = rent(alice, groupId, bob, start.plusDays(5), null);

        resume(alice, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.nextDate").value(start.plusDays(5).toString()));
        call(alice, delete("/api/recurring-expenses/" + id)).andExpect(status().isNoContent());
        resume(mallory, id).andExpect(status().isForbidden());
        resume(alice, Long.MAX_VALUE).andExpect(status().isNotFound());
        call(alice, get("/api/recurring-expenses/" + id)).andExpect(jsonPath("$.active").value(false));
    }

    private ResultActions resume(Session caller, long id) throws Exception {
        return call(caller, post("/api/recurring-expenses/" + id + "/resume"));
    }

    /** Monthly rent of 2,000 paid by Alice, shared with one other person; returns its id. */
    private long rent(Session alice, long groupId, Session other, LocalDate start, LocalDate end) throws Exception {
        Map<String, Object> body = expenseBody(groupId, alice, "2000.00", "EQUAL", Share.of(alice), Share.of(other));
        body.put("description", "Rent");
        body.put("frequency", "MONTHLY");
        body.put("startDate", start.toString());
        if (end != null) {
            body.put("endDate", end.toString());
        }
        return read(call(alice, postJson("/api/recurring-expenses", body)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private JsonNode groupExpenses(Session caller, long groupId) throws Exception {
        return read(call(caller, get("/api/groups/" + groupId + "/expenses").param("size", "100"))
                .andExpect(status().isOk())).get("content");
    }
}
