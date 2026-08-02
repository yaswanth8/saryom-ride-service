package com.saryom.rideservice.web.dto;

import com.saryom.rideservice.domain.Booking;
import com.saryom.rideservice.domain.Ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RideDetailResponse(
        UUID id,
        String driverId,
        String originText,
        Double originLat,
        Double originLng,
        String destinationText,
        Double destinationLat,
        Double destinationLng,
        Instant departAt,
        int seatsTotal,
        int seatsAvailable,
        BigDecimal pricePerSeat,
        String notes,
        String status,
        Instant createdAt,
        boolean isDriver,
        /** The viewer's own live booking, if any — drives "Book" vs "Cancel booking". */
        UUID myBookingId,
        int myseats,
        /**
         * Only ever populated for the driver. A rider has no business seeing who
         * else is in the car before the trip.
         */
        List<RiderSummary> riders) {

    public record RiderSummary(String riderId, int seats, Instant bookedAt) {
        static RiderSummary from(Booking b) {
            return new RiderSummary(b.getRiderId(), b.getSeats(), b.getCreatedAt());
        }
    }

    public static RideDetailResponse from(Ride r, String viewerId, Booking myBooking, List<Booking> confirmed) {
        boolean isDriver = viewerId != null && r.isDrivenBy(viewerId);
        return new RideDetailResponse(
                r.getId(), r.getDriverId(),
                r.getOriginText(), r.getOriginLat(), r.getOriginLng(),
                r.getDestinationText(), r.getDestinationLat(), r.getDestinationLng(),
                r.getDepartAt(), r.getSeatsTotal(), r.getSeatsAvailable(),
                r.getPricePerSeat(), r.getNotes(), r.getStatus().name(), r.getCreatedAt(),
                isDriver,
                myBooking == null ? null : myBooking.getId(),
                myBooking == null ? 0 : myBooking.getSeats(),
                isDriver ? confirmed.stream().map(RiderSummary::from).toList() : List.of());
    }
}
