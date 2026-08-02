package com.saryom.rideservice.domain;

public enum RideStatus {
    /** Accepting bookings. */
    OPEN,
    /** Every seat is taken; still upcoming, but not bookable. */
    FULL,
    /** The driver marked the trip as done. */
    COMPLETED,
    /** Called off by the driver. Bookings are released. */
    CANCELLED
}
