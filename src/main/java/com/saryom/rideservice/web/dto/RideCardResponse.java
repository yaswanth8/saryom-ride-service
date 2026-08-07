package com.saryom.rideservice.web.dto;

import com.saryom.rideservice.domain.Ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Feed row. Deliberately smaller than the detail payload — a browse page may hold a hundred of these. */
public record RideCardResponse(
        UUID id,
        String driverId,
        String originText,
        String destinationText,
        Instant departAt,
        int seatsAvailable,
        int seatsTotal,
        BigDecimal pricePerSeat,
        String status,
        /**
         * Trip preferences ride here as well as on the detail payload: they are
         * what a rider filters the feed by eye for, and making them a reason to
         * open the page defeats the point of showing them at all.
         */
        String bagSize,
        boolean smokingAllowed,
        boolean petsAllowed,
        /** Distance from the caller to the pickup point; null when no coordinates were supplied. */
        Double distanceMiles) {

    public static RideCardResponse from(Ride r, Double distanceMiles) {
        return new RideCardResponse(r.getId(), r.getDriverId(), r.getOriginText(), r.getDestinationText(),
                r.getDepartAt(), r.getSeatsAvailable(), r.getSeatsTotal(), r.getPricePerSeat(),
                r.getStatus().name(), r.getBagSize().name(), r.isSmokingAllowed(), r.isPetsAllowed(),
                distanceMiles);
    }
}
