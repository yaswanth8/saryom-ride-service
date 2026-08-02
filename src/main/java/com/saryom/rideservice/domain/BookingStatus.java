package com.saryom.rideservice.domain;

public enum BookingStatus {
    CONFIRMED,
    /** Released by the rider, or by the driver cancelling the whole ride. */
    CANCELLED
}
