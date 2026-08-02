package com.saryom.rideservice.auth;

/**
 * Verifies a Firebase ID token and returns the caller identity. Abstraction (DIP)
 * so the HTTP/security layer never depends on the Firebase SDK directly.
 */
public interface TokenVerifier {

    VerifiedUser verify(String idToken) throws InvalidTokenException;
}
