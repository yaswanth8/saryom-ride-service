package com.saryom.rideservice.events;

import java.time.Instant;
import java.util.UUID;

/** A new ride is on offer. Topic {@code ride.posted}. */
public record RidePostedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        String driverId,
        String originText,
        String destinationText,
        Instant departAt,
        int seatsTotal) {

    public static RidePostedEvent of(UUID rideId, String driverId, String originText,
                                     String destinationText, Instant departAt, int seatsTotal) {
        return new RidePostedEvent(UUID.randomUUID(), Instant.now(),
                rideId, driverId, originText, destinationText, departAt, seatsTotal);
    }
}
