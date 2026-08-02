package com.saryom.rideservice.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A rider gave their seats back. Topic {@code ride.booking_cancelled}.
 *
 * <p>Separate from {@link RideCancelledEvent}: there the trip is gone and every
 * rider is stranded; here the trip is fine and the driver simply has a seat to
 * fill. Same table, opposite meaning — one message for both would be wrong for
 * whichever party received it.
 */
public record BookingCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID bookingId,
        String driverId,
        String riderId,
        int seatsReleased) {

    public static BookingCancelledEvent of(UUID rideId, UUID bookingId, String driverId,
                                           String riderId, int seatsReleased) {
        return new BookingCancelledEvent(UUID.randomUUID(), Instant.now(),
                rideId, bookingId, driverId, riderId, seatsReleased);
    }
}
