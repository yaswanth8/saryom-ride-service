package com.saryom.rideservice.web.dto;

import com.saryom.rideservice.domain.Ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Feed row. Deliberately smaller than the detail payload — a browse page may hold a hundred of these. */
public record RideCardResponse(
        UUID id,
        String originText,
        String destinationText,
        Instant departAt,
        int seatsAvailable,
        BigDecimal pricePerSeat,
        String status,
        /** Distance from the caller to the pickup point; null when no coordinates were supplied. */
        Double distanceMiles) {

    public static RideCardResponse from(Ride r, Double distanceMiles) {
        return new RideCardResponse(r.getId(), r.getOriginText(), r.getDestinationText(),
                r.getDepartAt(), r.getSeatsAvailable(), r.getPricePerSeat(),
                r.getStatus().name(), distanceMiles);
    }
}
