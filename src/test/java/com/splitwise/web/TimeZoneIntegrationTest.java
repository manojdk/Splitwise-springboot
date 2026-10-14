package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Today" is the user's, from the X-Time-Zone header the web app sends, not the server's.
 */
class TimeZoneIntegrationTest extends ApiTestSupport {

    private static final ZoneId SAMOA = ZoneId.of("Pacific/Pago_Pago");     // UTC-11
    private static final ZoneId KIRITIMATI = ZoneId.of("Pacific/Kiritimati"); // UTC+14

    /** 05:00 UTC: already "tomorrow" in Kiritimati, still "yesterday" evening in Samoa, whatever the server's zone. */
    @BeforeEach
    void fiveInTheMorningUtc() {
        Instant target = clock.instant().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(5));
        clock.advance(Duration.between(clock.instant(), target));
    }

    private LocalDate todayIn(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    @Test
    void anExpenseWithoutADateGetsTheUsersDateWhereverTheyAre() throws Exception {
        Session alice = user("Alice");
        long groupId = group(alice);

        for (ZoneId zone : new ZoneId[]{SAMOA, KIRITIMATI}) {
            call(alice, postJson("/api/expenses", expenseBody(groupId, alice, "10.00", "EQUAL", Share.of(alice)))
                    .header(CallerToday.HEADER, zone.getId()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.date").value(todayIn(zone).toString()));
        }
        // Samoa and Kiritimati are 25 hours apart, so these were two different dates
        assertThat(todayIn(SAMOA)).isNotEqualTo(todayIn(KIRITIMATI));
    }

    @Test
    void aRecurringExpenseCanStartOnTheUsersTodayEvenWhenTheServerIsADayAhead() throws Exception {
        Session alice = user("Alice");
        long groupId = group(alice);
        Map<String, Object> body = expenseBody(groupId, alice, "999.00", "EQUAL", Share.of(alice));
        body.put("description", "Wifi");
        body.put("frequency", "MONTHLY");
        body.put("startDate", todayIn(SAMOA).toString());

        // Without the user's zone, Samoa's today is the server's yesterday
        call(alice, postJson("/api/recurring-expenses", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate cannot be in the past"));
        call(alice, postJson("/api/recurring-expenses", body).header(CallerToday.HEADER, SAMOA.getId()))
                .andExpect(status().isCreated());
    }

    @Test
    void anUnknownZoneIsIgnoredRatherThanRefused() throws Exception {
        Session alice = user("Alice");
        long groupId = group(alice);

        call(alice, postJson("/api/expenses", expenseBody(groupId, alice, "10.00", "EQUAL", Share.of(alice)))
                .header(CallerToday.HEADER, "Mars/Olympus_Mons"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(todayIn(clock.getZone()).toString()));
    }
}
