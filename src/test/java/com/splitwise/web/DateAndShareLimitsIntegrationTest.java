package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dates are kept to a sensible window (2000 to a year from the caller's today), so a typo like 2099 or 1925 is
 * caught instead of sitting forever at the top or bottom of every list; and an expense has at most 100 people.
 */
class DateAndShareLimitsIntegrationTest extends ApiTestSupport {

    private static final String TOO_EARLY = "must be on or after 2000-01-01";
    private static final String TOO_LATE = "must be at most a year from today";

    @Test
    void anExpenseDateMustBeBetween2000AndAYearFromToday() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);
        LocalDate today = LocalDate.now(clock);

        expenseOn(alice, flat, bob, "1999-12-31").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date: " + TOO_EARLY));
        expenseOn(alice, flat, bob, today.plusYears(1).plusDays(1).toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date: " + TOO_LATE));

        expenseOn(alice, flat, bob, "2000-01-01").andExpect(status().isCreated());
        long id = read(expenseOn(alice, flat, bob, today.plusYears(1).toString()).andExpect(status().isCreated()))
                .get("id").asLong();

        // The same rule on an edit
        Map<String, Object> edit = updateBody(0, alice, "30.00", "EQUAL", Share.of(alice), Share.of(bob));
        edit.put("date", "2099-01-01");
        updateExpense(alice, id, edit).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date: " + TOO_LATE));
    }

    /** "Today" is the caller's: late on 31 Dec in Kolkata a date a year from 1 Jan is still too far. */
    @Test
    void aYearFromTodayIsCountedInTheCallersTimeZone() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);
        // 31 Dec 15:00 in New York, already 1 Jan 01:30 in Kolkata
        clock.advance(Duration.between(clock.instant(), Instant.parse("2026-12-31T20:00:00Z")));

        Map<String, Object> body = expenseBody(flat, alice, "30.00", "EQUAL", Share.of(alice), Share.of(bob));
        body.put("date", "2028-01-01");
        call(alice, postJson("/api/expenses", body).header(CallerToday.HEADER, "America/New_York"))
                .andExpect(status().isBadRequest());
        call(alice, postJson("/api/expenses", body).header(CallerToday.HEADER, "Asia/Kolkata"))
                .andExpect(status().isCreated());
    }

    @Test
    void aPaymentDateHasTheSameWindow() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);

        for (String date : List.of("1999-12-31", "2099-01-01")) {
            call(alice, postJson("/api/settlements", Map.of("fromUserId", bob.id(), "toUserId", alice.id(),
                    "amount", new BigDecimal("10.00"), "groupId", flat, "date", date)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("date: " + (date.startsWith("1999") ? TOO_EARLY : TOO_LATE)));
        }
    }

    @Test
    void aRecurringExpenseStartsWithinAYearAndEndsWithin50Years() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);
        LocalDate today = LocalDate.now(clock);

        recurring(alice, flat, bob, today.plusYears(1).plusDays(1), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate: " + TOO_LATE));
        recurring(alice, flat, bob, today.plusDays(1), today.plusDays(1).plusYears(50).plusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("endDate cannot be more than 50 years after startDate"));

        recurring(alice, flat, bob, today.plusYears(1), today.plusYears(51)).andExpect(status().isCreated());
    }

    @Test
    void anExpenseHasAtMost100People() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);

        Share[] shares = new Share[101];
        for (int i = 0; i < shares.length; i++) {
            shares[i] = new Share(alice.id() + 1000 + i, null);
        }
        addExpense(alice, expenseBody(flat, alice, "101.00", "EQUAL", shares))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("shares: must have at most 100 people"));
    }

    private ResultActions expenseOn(Session alice, long groupId, Session bob, String date) throws Exception {
        Map<String, Object> body = expenseBody(groupId, alice, "30.00", "EQUAL", Share.of(alice), Share.of(bob));
        body.put("date", date);
        return addExpense(alice, body);
    }

    private ResultActions recurring(Session alice, long groupId, Session bob, LocalDate start, LocalDate end)
            throws Exception {
        Map<String, Object> body = expenseBody(groupId, alice, "30.00", "EQUAL", Share.of(alice), Share.of(bob));
        body.put("frequency", "MONTHLY");
        body.put("startDate", start.toString());
        if (end != null) {
            body.put("endDate", end.toString());
        }
        return call(alice, postJson("/api/recurring-expenses", body));
    }
}
