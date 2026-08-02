package com.saryom.rideservice.error;

/** Thrown when an endpoint needs an authenticated caller but none is present; mapped to 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
