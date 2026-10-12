package com.splitwise.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A clock tests can move: forward by a duration, or to a given date. Follows real time otherwise.
 * {@link #reset()} goes back to real time.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Duration offset = Duration.ZERO;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void advance(Duration amount) {
        offset = offset.plus(amount);
    }

    /** Moves "today" to the given date (keeping the current time of day). */
    public void setDate(LocalDate date) {
        LocalDate today = LocalDate.now(this);
        offset = offset.plusDays(date.toEpochDay() - today.toEpochDay());
    }

    public void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        MutableClock copy = new MutableClock(newZone);
        copy.offset = offset;
        return copy;
    }
}
