package com.saryom.rideservice.error;

/** Thrown when an action conflicts with the resource's current state; mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
