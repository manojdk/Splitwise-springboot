package com.splitwise.security;

import com.splitwise.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing, in memory (one instance; running several would need a shared store):
 * <ul>
 *   <li>per account: after {@code maxFailures} wrong passwords within {@code failureWindow}, sign-in for that email is
 *       refused until the oldest failure is older than the window; a successful sign-in clears the count;</li>
 *   <li>per client address: at most {@code maxRequestsPerMinute} sign-in and sign-up requests a minute, which also
 *       caps the (deliberately slow) password hashing one client can make the server do.</li>
 * </ul>
 * Behind a reverse proxy the client address is the proxy's unless forwarded headers are trusted
 * ({@code server.forward-headers-strategy}).
 */
@Component
public class LoginThrottle {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    /** Above this many tracked keys, expired ones are dropped, so the maps cannot grow without bound. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration failureWindow;
    private final int maxRequestsPerMinute;
    private final Map<String, Deque<Instant>> failuresByEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> requestsByClient = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock,
                         @Value("${app.auth.max-failures:5}") int maxFailures,
                         @Value("${app.auth.failure-window:PT15M}") Duration failureWindow,
                         @Value("${app.auth.max-requests-per-minute:30}") int maxRequestsPerMinute) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.failureWindow = failureWindow;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /** Counts one sign-in or sign-up request from this client address, or refuses it. */
    public void countRequest(String clientAddress) {
        Instant now = clock.instant();
        Deque<Instant> requests = requestsByClient.computeIfAbsent(clientAddress, k -> new ArrayDeque<>());
        synchronized (requests) {
            dropUpTo(requests, now.minus(MINUTE));
            if (requests.size() >= maxRequestsPerMinute) {
                throw tooMany("Too many requests; try again in a minute", requests.peekFirst().plus(MINUTE), now);
            }
            requests.addLast(now);
        }
        cleanUpIfLarge(requestsByClient, MINUTE, now);
    }

    /** Refuses a sign-in for this email while it has too many recent wrong passwords. */
    public void checkAccount(String email) {
        Deque<Instant> failures = failuresByEmail.get(email);
        if (failures == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (failures) {
            dropUpTo(failures, now.minus(failureWindow));
            if (failures.size() >= maxFailures) {
                throw tooMany("Too many wrong passwords for this account; try again later",
                        failures.peekFirst().plus(failureWindow), now);
            }
        }
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        Deque<Instant> failures = failuresByEmail.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (failures) {
            failures.addLast(now);
        }
        cleanUpIfLarge(failuresByEmail, failureWindow, now);
    }

    public void recordSuccess(String email) {
        failuresByEmail.remove(email);
    }

    private static TooManyRequestsException tooMany(String message, Instant allowedAt, Instant now) {
        Duration wait = Duration.between(now, allowedAt);
        return new TooManyRequestsException(message, wait.isNegative() ? Duration.ZERO : wait);
    }

    /** Removes the times at or before {@code cutoff} (oldest first). */
    private static void dropUpTo(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.removeFirst();
        }
    }

    private static void cleanUpIfLarge(Map<String, Deque<Instant>> map, Duration window, Instant now) {
        if (map.size() <= CLEANUP_THRESHOLD) {
            return;
        }
        Instant cutoff = now.minus(window);
        map.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                dropUpTo(entry.getValue(), cutoff);
                return entry.getValue().isEmpty();
            }
        });
    }
}
