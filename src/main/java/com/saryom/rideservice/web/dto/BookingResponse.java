package com.saryom.rideservice.web.dto;

import com.saryom.rideservice.domain.Booking;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID rideId,
        String riderId,
        int seats,
        String status,
        Instant createdAt) {

    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getRideId(), b.getRiderId(),
                b.getSeats(), b.getStatus().name(), b.getCreatedAt());
    }
}
