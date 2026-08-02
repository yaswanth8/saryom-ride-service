package com.saryom.rideservice.domain;

import org.springframework.data.domain.Sort;

public enum RideSort {
    /** Soonest departure first — the default, because a ride next week is
     *  useless to someone travelling today. */
    DEPARTING_SOON,
    NEAREST,
    CHEAPEST;

    public Sort toSort() {
        return switch (this) {
            case DEPARTING_SOON -> Sort.by(Sort.Direction.ASC, "departAt");
            case CHEAPEST -> Sort.by(Sort.Direction.ASC, "pricePerSeat");
            // Distance is computed per-request from the caller's coordinates, so
            // it cannot be expressed as a column sort; the service orders those
            // in memory. Falling back to departure keeps the query valid.
            case NEAREST -> Sort.by(Sort.Direction.ASC, "departAt");
        };
    }
}
