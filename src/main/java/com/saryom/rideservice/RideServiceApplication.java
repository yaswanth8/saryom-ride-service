package com.saryom.rideservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling drives {@link com.saryom.rideservice.service.DepartedRideSweeper},
 * which closes rides whose departure has passed. Without
 * {@code @EnableScheduling} that job never fires and nothing reports a problem,
 * so {@code RideServiceApplicationTest} asserts the annotation is present.
 */
@SpringBootApplication
@EnableScheduling
public class RideServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}
