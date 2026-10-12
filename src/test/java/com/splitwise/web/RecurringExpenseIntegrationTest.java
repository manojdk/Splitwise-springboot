package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.recurring.RecurringExpenseJob;
import com.splitwise.service.RecurringExpenseService;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recurring expenses end to end, with the clock moved instead of waiting. The scheduler is off in tests; the
 * tests run the job themselves ({@link RecurringExpenseJob#runDueNow()}).
 */
class RecurringExpenseIntegrationTest extends ApiTestSupport {

    @Autowired
    private RecurringExpenseJob job;
    @Autowired
    private RecurringExpenseService recurringExpenseService;

    @Test
    void rentStartingTodayIsCreatedNowAndThenEveryMonthExactlyOnce() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        LocalDate today = LocalDate.now(clock);

        JsonNode rent = read(createRecurring(alice, groupId, "MONTHLY", today, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.nextDate").value(today.plusMonths(1).toString())));
        long rentId = rent.get("id").asLong();

        JsonNode expenses = groupExpenses(alice, groupId);
        assertThat(expenses).hasSize(1);
        assertThat(expenses.get(0).get("date").asText()).isEqualTo(today.toString());
        assertThat(expenses.get(0).get("recurringExpenseId").asLong()).isEqualTo(rentId);
        assertThat(expenses.get(0).get("category").asText()).isEqualTo("RENT");

        assertThat(recurringExpenseService.generateDue(rentId)).as("nothing more is due today").isZero();
        clock.advance(Duration.ofDays(31));
        job.runDueNow();
        assertThat(recurringExpenseService.generateDue(rentId)).as("running again the same day creates nothing")
                .isZero();

        assertThat(groupExpenses(alice, groupId)).hasSize(2);
        assertThat(balances(alice, groupId)).containsEntry(bob.id(), money("-24000.00"));
    }

    @Test
    void missedRunsAreCaughtUpWithTheRightDatesAndMonthEndsDoNotDrift() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        clock.setDate(LocalDate.of(2027, 1, 31));
        long id = read(createRecurring(alice, groupId, "MONTHLY", LocalDate.of(2027, 1, 31), null)
                .andExpect(status().isCreated())).get("id").asLong();

        clock.setDate(LocalDate.of(2027, 4, 30)); // the app was "down" for three months
        assertThat(recurringExpenseService.generateDue(id)).isEqualTo(3);

        List<String> dates = new ArrayList<>();
        groupExpenses(alice, groupId).forEach(e -> dates.add(e.get("date").asText()));
        assertThat(dates).containsExactly("2027-04-30", "2027-03-31", "2027-02-28", "2027-01-31");
    }

    @Test
    void aTemplateWithAnEndDateFinishesByItself() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        LocalDate today = LocalDate.now(clock);
        long id = read(createRecurring(alice, groupId, "WEEKLY", today, today.plusDays(14))).get("id").asLong();

        clock.advance(Duration.ofDays(60));
        recurringExpenseService.generateDue(id);

        assertThat(groupExpenses(alice, groupId)).hasSize(3); // today, +7, +14
        call(alice, get("/api/recurring-expenses/" + id))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.nextDate").doesNotExist())
                .andExpect(jsonPath("$.stoppedReason", containsString("Ended on")));
    }

    @Test
    void stoppingKeepsPastExpensesAndCreatesNoMore() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session outsider = user("Mallory");
        long groupId = group(alice, bob);
        long id = read(createRecurring(alice, groupId, "MONTHLY", LocalDate.now(clock), null)).get("id").asLong();

        call(outsider, delete("/api/recurring-expenses/" + id)).andExpect(status().isForbidden());
        call(bob, delete("/api/recurring-expenses/" + id)).andExpect(status().isNoContent());
        call(bob, delete("/api/recurring-expenses/" + id)).andExpect(status().isNoContent()); // idempotent
        clock.advance(Duration.ofDays(40));
        assertThat(recurringExpenseService.generateDue(id)).isZero();

        assertThat(groupExpenses(alice, groupId)).hasSize(1);
        call(alice, get("/api/recurring-expenses/" + id))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.stoppedReason").value("Stopped by Bob"));
        call(alice, get("/api/recurring-expenses")).andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
        call(outsider, get("/api/recurring-expenses")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ifSomeoneInItLeavesTheGroupTheTemplatePausesInsteadOfCreatingABadExpense() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        long id = read(createRecurring(alice, groupId, "MONTHLY", tomorrow, null, carol)).get("id").asLong();
        removeMember(carol, groupId, carol).andExpect(status().isOk()); // nothing owed yet, so she may leave

        clock.advance(Duration.ofDays(1));
        job.runDueNow();

        assertThat(groupExpenses(alice, groupId)).isEmpty();
        call(alice, get("/api/recurring-expenses/" + id))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.stoppedReason").value("Paused: Carol is no longer a member of the group"));
        call(alice, get("/api/groups/" + groupId + "/activity"))
                .andExpect(jsonPath("$.content[0].type").value("RECURRING_PAUSED"));
    }

    @Test
    void invalidTemplatesAreRejectedAndNothingIsSaved() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session outsider = user("Mallory");
        long groupId = group(alice, bob);
        LocalDate today = LocalDate.now(clock);

        createRecurring(alice, groupId, "MONTHLY", today.minusDays(1), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("past")));
        createRecurring(alice, groupId, "MONTHLY", today, today.minusDays(1)).andExpect(status().isBadRequest());
        createRecurring(alice, groupId, "FORTNIGHTLY", today, null).andExpect(status().isBadRequest());
        createRecurring(outsider, groupId, "MONTHLY", today, null, bob).andExpect(status().isForbidden());
        Map<String, Object> badSplit = recurringBody(groupId, alice, "MONTHLY", today, null, Share.of(alice, "1.00"),
                Share.of(bob, "1.00"));
        badSplit.put("splitType", "EXACT");
        call(alice, postJson("/api/recurring-expenses", badSplit)).andExpect(status().isBadRequest());

        assertThat(groupExpenses(alice, groupId)).isEmpty();
        call(alice, get("/api/recurring-expenses")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aNonGroupRecurringExpenseIsVisibleOnlyToThePeopleInIt() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session outsider = user("Mallory");
        Map<String, Object> body = expenseBody(null, alice, "500.00", "EQUAL", Share.of(alice), Share.of(bob));
        body.put("description", "Netflix");
        body.put("frequency", "MONTHLY");
        body.put("startDate", LocalDate.now(clock).plusDays(3).toString());
        long id = read(call(alice, postJson("/api/recurring-expenses", body)).andExpect(status().isCreated()))
                .get("id").asLong();

        call(bob, get("/api/recurring-expenses"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].groupId").doesNotExist())
                .andExpect(jsonPath("$[0].shares", hasSize(2)));
        call(outsider, get("/api/recurring-expenses")).andExpect(jsonPath("$", hasSize(0)));
        call(outsider, get("/api/recurring-expenses/" + id)).andExpect(status().isForbidden());

        clock.advance(Duration.ofDays(3));
        assertThat(recurringExpenseService.generateDue(id)).isEqualTo(1);
        assertThat(myOverallBalance(bob)).isEqualByComparingTo("-250.00");
    }

    @Test
    void twoSchedulerRunsAtTheSameTimeCreateEachOccurrenceOnce() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        long id = read(createRecurring(alice, groupId, "WEEKLY", LocalDate.now(clock), null)).get("id").asLong();
        clock.advance(Duration.ofDays(21)); // three more occurrences due

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(pool.submit(() -> {
                    go.await();
                    return recurringExpenseService.generateDue(id);
                }));
            }
            go.countDown();
            int created = 0;
            for (Future<Integer> run : runs) {
                created += run.get(30, TimeUnit.SECONDS);
            }
            assertThat(created).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(groupExpenses(alice, groupId)).hasSize(4);
    }

    // ------------------------------------------------------------------ helpers

    /** Rent of 24,000 paid by Alice, split equally among Alice and the others given (default: everyone else). */
    private ResultActions createRecurring(Session alice, long groupId, String frequency, LocalDate start,
                                          LocalDate end, Session... others) throws Exception {
        List<Share> shares = new ArrayList<>(List.of(Share.of(alice)));
        if (others.length == 0) {
            JsonNode group = read(call(alice, get("/api/groups/" + groupId)));
            group.get("members").forEach(m -> {
                if (m.get("id").asLong() != alice.id()) {
                    shares.add(new Share(m.get("id").asLong(), null));
                }
            });
        } else {
            for (Session other : others) {
                shares.add(Share.of(other));
            }
        }
        return call(alice, postJson("/api/recurring-expenses",
                recurringBody(groupId, alice, frequency, start, end, shares.toArray(Share[]::new))));
    }

    private Map<String, Object> recurringBody(long groupId, Session payer, String frequency, LocalDate start,
                                              LocalDate end, Share... shares) {
        Map<String, Object> body = expenseBody(groupId, payer, "24000.00", "EQUAL", shares);
        body.put("description", "Rent");
        body.put("category", "RENT");
        body.put("frequency", frequency);
        body.put("startDate", start.toString());
        if (end != null) {
            body.put("endDate", end.toString());
        }
        return body;
    }

    private JsonNode groupExpenses(Session caller, long groupId) throws Exception {
        return read(call(caller, get("/api/groups/" + groupId + "/expenses")).andExpect(status().isOk()));
    }
}
