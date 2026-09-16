package com.saryom.rideservice.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * When the repeated ride leaves. Everything else is taken from the ride being
 * repeated, which is the whole point.
 *
 * <p>{@code @Future} rejects a past date at the edge, matching
 * {@link CreateRideRequest}: a ride that has already left cannot be booked, so
 * accepting one only creates something invisible to every query.
 */
public record RepeatRideRequest(@NotNull @Future Instant departAt) {
}
