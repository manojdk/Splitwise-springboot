package com.splitwise.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" for the person making the request, in their own time zone. The web app sends it as
 * {@code X-Time-Zone: Asia/Kolkata}; without a valid one (other clients, the scheduler), the server's zone is used.
 *
 * <p>So an expense entered without a date gets the user's date, not the server's, and a recurring expense can start
 * on the user's today even when the server (often on UTC) is already a day ahead or behind.
 */
@Component
public class CallerToday {

    public static final String HEADER = "X-Time-Zone";

    private final Clock clock;

    public CallerToday(Clock clock) {
        this.clock = clock;
    }

    public LocalDate get() {
        return LocalDate.now(clock.withZone(callerZone()));
    }

    private ZoneId callerZone() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String zone = request.getHeader(HEADER);
            // Region ids are short ("America/Argentina/Buenos_Aires" is the longest kind); anything else is ignored
            if (zone != null && !zone.isBlank() && zone.length() <= 64) {
                try {
                    return ZoneId.of(zone.trim());
                } catch (DateTimeException ignored) {
                    // Not a zone id: fall back to the server's
                }
            }
        }
        return clock.getZone();
    }
}
