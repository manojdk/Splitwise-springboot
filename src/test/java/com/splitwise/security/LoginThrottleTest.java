package com.splitwise.security;

import com.splitwise.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class LoginThrottleTest {

    /** Stands still until moved, so waits can be checked to the millisecond. */
    private static final class StepClock extends Clock {
        private Instant now = Instant.parse("2026-10-14T10:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final StepClock clock = new StepClock();
    private final LoginThrottle throttle = new LoginThrottle(clock, 3, Duration.ofMinutes(15), 4);

    @Test
    void anAccountIsLockedAfterTooManyWrongPasswordsUntilTheOldestOneExpires() {
        throttle.recordFailure("a@x.com");
        clock.advance(Duration.ofMinutes(5));
        throttle.recordFailure("a@x.com");
        throttle.recordFailure("a@x.com");

        TooManyRequestsException refused = catchThrowableOfType(TooManyRequestsException.class,
                () -> throttle.checkAccount("a@x.com"));
        assertThat(refused.getRetryAfter()).isEqualTo(Duration.ofMinutes(10));
        assertThatCode(() -> throttle.checkAccount("other@x.com")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> throttle.checkAccount("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulSignInClearsTheCount() {
        throttle.recordFailure("a@x.com");
        throttle.recordFailure("a@x.com");
        throttle.recordSuccess("a@x.com");
        throttle.recordFailure("a@x.com");

        assertThatCode(() -> throttle.checkAccount("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void oneClientGetsAFixedNumberOfRequestsAMinute() {
        for (int i = 0; i < 4; i++) {
            throttle.countRequest("10.0.0.1");
            clock.advance(Duration.ofSeconds(10));
        }
        TooManyRequestsException refused = catchThrowableOfType(TooManyRequestsException.class,
                () -> throttle.countRequest("10.0.0.1"));
        // The first request was 40 s ago, so a slot frees up in 20 s
        assertThat(refused.getRetryAfter()).isEqualTo(Duration.ofSeconds(20));
        assertThatCode(() -> throttle.countRequest("10.0.0.2")).doesNotThrowAnyException();

        clock.advance(Duration.ofSeconds(20));
        assertThatCode(() -> throttle.countRequest("10.0.0.1")).doesNotThrowAnyException();
    }
}
