package com.gatepass.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock the tests can move: lets us say "it is now 22:00" without waiting. */
class TestClock extends Clock {

    private final ZoneId zone;
    private volatile LocalDateTime now;

    TestClock(ZoneId zone, LocalDateTime start) {
        this.zone = zone;
        this.now = start;
    }

    void set(LocalDateTime newTime) {
        this.now = newTime;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new TestClock(newZone, now);
    }

    @Override
    public Instant instant() {
        return now.atZone(zone).toInstant();
    }
}
