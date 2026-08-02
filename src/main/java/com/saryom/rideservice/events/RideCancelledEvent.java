package com.saryom.rideservice.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The driver called the trip off. Topic {@code ride.cancelled}.
 *
 * <p>Carries every affected rider, because they each need to know their travel
 * plan just disappeared — this is the most time-critical notification the
 * service emits, and the rider list is only known here.
 */
public record RideCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        String driverId,
        List<String> affectedRiderIds,
        String originText,
        String destinationText,
        Instant departAt) {

    public static RideCancelledEvent of(UUID rideId, String driverId, List<String> affectedRiderIds,
                                        String originText, String destinationText, Instant departAt) {
        return new RideCancelledEvent(UUID.randomUUID(), Instant.now(),
                rideId, driverId, List.copyOf(affectedRiderIds), originText, destinationText, departAt);
    }
}
