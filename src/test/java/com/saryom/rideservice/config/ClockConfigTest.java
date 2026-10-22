package com.saryom.rideservice.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void theClockNeverProducesMorePrecisionThanPostgresStores() {
        // TIMESTAMPTZ keeps microseconds and rounds anything finer, so a
        // sub-microsecond instant would be returned by a write and then read
        // back as a different, rounded one.
        Clock clock = new ClockConfig().clock();
        for (int i = 0; i < 1_000; i++) {
            assertThat(clock.instant().getNano() % 1_000).isZero();
        }
    }
}
