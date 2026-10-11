package com.saryom.rideservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        // Microsecond ticks, matching what Postgres stores. TIMESTAMPTZ keeps
        // microseconds and rounds anything finer, so with the JVM's nanosecond
        // clock a write returned one instant and every later read another: a
        // reservation's deadline came back as ...03.399346850Z from the reserve
        // call and ...03.399347Z when re-read — later, not merely shorter.
        // Ticking at the stored precision makes the value a write returns the
        // value it will always read back.
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
