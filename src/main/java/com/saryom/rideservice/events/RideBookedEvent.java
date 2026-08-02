package com.saryom.rideservice.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A rider took seats. Topic {@code ride.booked}.
 *
 * <p>Carries the driver as well as the rider: the driver is the one who needs
 * telling, and a consumer should not have to call back into this service to
 * find out who to notify.
 */
public record RideBookedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        UUID bookingId,
        String driverId,
        String riderId,
        int seats,
        String originText,
        String destinationText) {

    public static RideBookedEvent of(UUID rideId, UUID bookingId, String driverId, String riderId,
                                     int seats, String originText, String destinationText) {
        return new RideBookedEvent(UUID.randomUUID(), Instant.now(),
                rideId, bookingId, driverId, riderId, seats, originText, destinationText);
    }
}
