package com.saryom.rideservice.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The trip happened. Topic {@code ride.completed}.
 *
 * <p>Carries every rider alongside the driver, because the fact this event
 * exists to record is not "a ride ended" but "these specific people travelled
 * together". That is what makes a review earned rather than asserted: without
 * it, user-service has no way to tell a real passenger from a stranger, and
 * ratings degrade into something anyone can leave about anyone.
 *
 * <p>{@code driverClosed} distinguishes a driver saying the trip ran from the
 * sweeper closing a ride nobody touched. Both are worth reviewing — the
 * departure passed either way — but only the first is a positive claim by a
 * human, and a consumer may reasonably weigh them differently.
 */
public record RideCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        String driverId,
        List<String> riderIds,
        String originText,
        String destinationText,
        Instant departedAt,
        boolean driverClosed) {

    public static RideCompletedEvent of(UUID rideId, String driverId, List<String> riderIds,
                                        String originText, String destinationText,
                                        Instant departedAt, boolean driverClosed) {
        return new RideCompletedEvent(UUID.randomUUID(), Instant.now(),
                rideId, driverId, List.copyOf(riderIds), originText, destinationText,
                departedAt, driverClosed);
    }
}
