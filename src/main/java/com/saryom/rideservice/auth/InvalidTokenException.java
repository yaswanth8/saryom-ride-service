package com.saryom.rideservice.auth;

/** Raised when an Authorization bearer token is missing, malformed, or rejected. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
