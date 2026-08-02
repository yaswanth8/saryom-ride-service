package com.saryom.rideservice.auth;

import com.saryom.rideservice.error.UnauthorizedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessor for the authenticated Firebase identity of the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String uid() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof String uid ? uid : null;
    }

    /** The uid of the authenticated caller, or a 401 if the request is anonymous. */
    public static String requireUid() {
        String uid = uid();
        if (uid == null) {
            throw new UnauthorizedException("Authentication is required for this action");
        }
        return uid;
    }

    /** The full verified identity (uid, email, displayName) captured at authentication time. */
    public static VerifiedUser details() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof VerifiedUser user) {
            return user;
        }
        return null;
    }
}
