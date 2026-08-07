package com.saryom.rideservice.service;

import com.saryom.rideservice.domain.Ride;
import com.saryom.rideservice.domain.RideRepository;
import com.saryom.rideservice.domain.RideStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Closes out rides whose departure has passed.
 *
 * <p>A ride only left OPEN by the driver marking it complete, and drivers
 * forget — the trip is over, they are getting out of the car. Nothing else
 * moved the row, so a ride from last month sat in "My trips" as OPEN forever,
 * and the riders on it kept a departed trip in their travel plans.
 *
 * <p>Browse already hid these (it filters on {@code departAt > now}), which is
 * exactly why the problem was easy to miss: the public feed looked correct
 * while every driver's and rider's own list slowly filled with dead rides.
 *
 * <p>The grace period matters. Closing a ride the moment it departs would fight
 * the driver: trips run late, and a driver marking their own trip complete an
 * hour after setting off should not find the service already did it. A day is
 * long enough that anyone still interested has acted.
 *
 * <p>Safe on more than one instance. Each ride is closed in its own
 * transaction, {@code Ride} carries a {@code @Version}, and the status is
 * re-checked inside — so a concurrent sweeper loses the write and moves on
 * rather than double-closing.
 */
@Component
public class DepartedRideSweeper {

    private static final Logger log = LoggerFactory.getLogger(DepartedRideSweeper.class);

    /** Bounds one sweep; a backlog drains over successive runs. */
    private static final int BATCH_SIZE = 200;

    private final RideRepository rides;
    private final RideService rideService;
    private final Clock clock;
    private final Duration grace;

    public DepartedRideSweeper(RideRepository rides,
                               RideService rideService,
                               Clock clock,
                               @Value("${saryom.rides.close-grace-hours:24}") long graceHours) {
        this.rides = rides;
        this.rideService = rideService;
        this.clock = clock;
        this.grace = Duration.ofHours(graceHours);
    }

    /** Fixed delay rather than fixed rate: runs cannot overlap or queue up. */
    @Scheduled(fixedDelayString = "${saryom.rides.sweep-interval-ms:900000}",
            initialDelayString = "${saryom.rides.sweep-initial-delay-ms:60000}")
    public void closeDepartedRides() {
        int closed = sweep();
        if (closed > 0) {
            log.info("Closed {} ride(s) that departed more than {}h ago", closed, grace.toHours());
        }
    }

    /** Exposed for tests; returns how many rides this sweep closed. */
    int sweep() {
        List<Ride> stale = rides.findByStatusInAndDepartAtBeforeOrderByDepartAtAsc(
                        List.of(RideStatus.OPEN, RideStatus.FULL),
                        clock.instant().minus(grace),
                        PageRequest.of(0, BATCH_SIZE))
                .getContent();

        int closed = 0;
        for (Ride ride : stale) {
            try {
                if (rideService.closeDeparted(ride.getId())) {
                    closed++;
                }
            } catch (RuntimeException e) {
                // One bad row must not abandon the batch; the next sweep retries.
                log.warn("Could not close departed ride {}", ride.getId(), e);
            }
        }
        return closed;
    }
}
