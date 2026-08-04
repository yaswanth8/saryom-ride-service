package com.saryom.rideservice;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class RideServiceApplicationTest {

    /**
     * Guards a silent failure mode.
     *
     * <p>Without {@code @EnableScheduling}, {@code DepartedRideSweeper} still
     * compiles, still passes its own unit tests, still deploys — and simply
     * never runs. Nothing fails; departed rides just quietly stay open forever.
     *
     * <p>This is not hypothetical: the annotation was dropped while scaffolding
     * this service from food-service, before there was a scheduled job to need
     * it, and the sweeper was added later without it being noticed.
     *
     * <p>Asserting on an annotation is unusual, but the alternative is a test
     * that boots the whole context to observe a job firing — far slower, and it
     * would still only prove scheduling works, not that it stayed enabled.
     */
    @Test
    void schedulingIsEnabledSoTheSweeperActuallyRuns() {
        assertThat(RideServiceApplication.class.getAnnotation(EnableScheduling.class))
                .as("@EnableScheduling on RideServiceApplication — without it "
                        + "DepartedRideSweeper never fires and nothing reports a problem")
                .isNotNull();
    }
}
