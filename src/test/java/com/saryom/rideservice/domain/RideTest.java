package com.saryom.rideservice.domain;

import com.saryom.rideservice.domain.BagSize;
import com.saryom.rideservice.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The seat and lifecycle invariants, tested on the entity that enforces them. */
class RideTest {

    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");
    private static final Instant DEPART = NOW.plusSeconds(86_400);

    private Ride ride(int seats) {
        return new Ride(UUID.randomUUID(), "driver-1", "Chicago", 41.87, -87.62,
                "Milwaukee", 43.04, -87.90, DEPART, seats, new BigDecimal("12.50"), null, BagSize.SMALL, false, false, NOW);
    }

    @Test
    void newRideOffersEverySeat() {
        Ride r = ride(3);
        assertThat(r.getSeatsAvailable()).isEqualTo(3);
        assertThat(r.getStatus()).isEqualTo(RideStatus.OPEN);
        assertThat(r.isBookable(NOW)).isTrue();
    }

    @Test
    void bookingTheLastSeatMarksTheRideFull() {
        Ride r = ride(2);
        r.book(2, NOW);
        assertThat(r.getSeatsAvailable()).isZero();
        // FULL is derived from the seat count, never set directly.
        assertThat(r.getStatus()).isEqualTo(RideStatus.FULL);
        assertThat(r.isBookable(NOW)).isFalse();
    }

    @Test
    void rejectsBookingMoreSeatsThanRemain() {
        Ride r = ride(2);
        r.book(1, NOW);
        assertThatThrownBy(() -> r.book(2, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only 1 seat");
        // The failed attempt must not have consumed anything.
        assertThat(r.getSeatsAvailable()).isEqualTo(1);
    }

    @Test
    void rejectsBookingAfterDeparture() {
        Ride r = ride(3);
        assertThatThrownBy(() -> r.book(1, DEPART.plusSeconds(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already departed");
    }

    @Test
    void rejectsBookingAFullRide() {
        Ride r = ride(1);
        r.book(1, NOW);
        assertThatThrownBy(() -> r.book(1, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer accepting");
    }

    @Test
    void releasingSeatsReopensAFullRide() {
        Ride r = ride(2);
        r.book(2, NOW);
        assertThat(r.getStatus()).isEqualTo(RideStatus.FULL);

        r.releaseSeats(1, NOW);
        assertThat(r.getSeatsAvailable()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(RideStatus.OPEN);
    }

    @Test
    void releasingNeverExceedsCapacity() {
        Ride r = ride(2);
        r.book(1, NOW);
        // A double-cancel must not conjure a third seat into a two-seat car.
        r.releaseSeats(1, NOW);
        r.releaseSeats(1, NOW);
        assertThat(r.getSeatsAvailable()).isEqualTo(2);
    }

    @Test
    void releasingSeatsOnACancelledRideChangesNothing() {
        Ride r = ride(3);
        r.book(1, NOW);
        r.cancel("driver-1", NOW);
        r.releaseSeats(1, NOW);
        // Reopening seats on a cancelled ride would put it back in the feed.
        assertThat(r.getStatus()).isEqualTo(RideStatus.CANCELLED);
    }

    @Test
    void onlyTheDriverCanCancelOrComplete() {
        Ride r = ride(3);
        assertThatThrownBy(() -> r.cancel("someone-else", NOW))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> r.complete("someone-else", NOW))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aCancelledRideCannotBeCompleted() {
        Ride r = ride(3);
        r.cancel("driver-1", NOW);
        assertThatThrownBy(() -> r.complete("driver-1", NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aCompletedRideCannotBeCancelled() {
        Ride r = ride(3);
        r.complete("driver-1", NOW);
        assertThatThrownBy(() -> r.cancel("driver-1", NOW))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsNonPositiveSeatCounts() {
        Ride r = ride(3);
        assertThatThrownBy(() -> r.book(0, NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aRideInThePastIsNotBookable() {
        Ride r = ride(3);
        assertThat(r.isBookable(DEPART.plusSeconds(1))).isFalse();
    }
}
