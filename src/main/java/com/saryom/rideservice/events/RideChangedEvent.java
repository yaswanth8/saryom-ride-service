package com.saryom.rideservice.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The terms of a ride people already booked have changed. Topic {@code ride.changed}.
 *
 * <p>Editing a ride was silent. A driver could move a 9am departure to 6am, or
 * double the price, and everyone holding a confirmed seat found out by turning
 * up — or by not turning up. Cancelling a ride has always notified its riders;
 * changing it out from under them did not, which is the worse of the two
 * because the rider still believes they have a plan.
 *
 * <p>Only departure time and price are carried, because they are the only edits
 * that can strand someone or cost them money. Retitling the route or rewording
 * the notes is not worth a push.
 *
 * <p>Both the old and new values travel so the consumer can say what actually
 * changed ("moved 3 hours earlier") rather than restating the ride.
 */
public record RideChangedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        String driverId,
        /** Confirmed riders only — a cancelled booking is not a plan being disrupted. */
        List<String> riderIds,
        String originText,
        String destinationText,
        Instant oldDepartAt,
        Instant newDepartAt,
        BigDecimal oldPricePerSeat,
        BigDecimal newPricePerSeat) {

    public static RideChangedEvent of(UUID rideId, String driverId, List<String> riderIds,
                                      String originText, String destinationText,
                                      Instant oldDepartAt, Instant newDepartAt,
                                      BigDecimal oldPricePerSeat, BigDecimal newPricePerSeat) {
        return new RideChangedEvent(UUID.randomUUID(), Instant.now(),
                rideId, driverId, List.copyOf(riderIds), originText, destinationText,
                oldDepartAt, newDepartAt, oldPricePerSeat, newPricePerSeat);
    }

    /** True when the departure moved at all, in either direction. */
    public boolean departureMoved() {
        return !oldDepartAt.equals(newDepartAt);
    }

    /** True when the seat price changed. Compared by value: 18.0 and 18.00 are the same price. */
    public boolean priceChanged() {
        return oldPricePerSeat.compareTo(newPricePerSeat) != 0;
    }
}
