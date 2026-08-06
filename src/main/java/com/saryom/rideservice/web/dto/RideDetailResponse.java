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
        /**
         * Trips this driver has completed. The screen's real question is "who am
         * I getting in a car with", and this is the one part of that answer this
         * service owns — name, rating and verification live in user-service.
         */
        long driverRidesCompleted,
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
        String bagSize,
        boolean smokingAllowed,
        boolean petsAllowed,
        String status,
        Instant createdAt,
        boolean isDriver,
        /** The viewer's own live booking, if any — drives "Book" vs "Cancel booking". */
        UUID myBookingId,
        int mySeats,
        /**
         * Deprecated misspelling of {@link #mySeats}, still emitted so a browser
         * holding the previous bundle keeps working while the new one rolls out.
         * Both fields always carry the same value. Remove once no deployed
         * frontend reads it — the frontend already prefers {@code mySeats}.
         */
        @Deprecated int myseats,
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

    public static RideDetailResponse from(Ride r, String viewerId, Booking myBooking,
                                          List<Booking> confirmed, long driverRidesCompleted) {
        boolean isDriver = viewerId != null && r.isDrivenBy(viewerId);
        int seats = myBooking == null ? 0 : myBooking.getSeats();
        return new RideDetailResponse(
                r.getId(), r.getDriverId(), driverRidesCompleted,
                r.getOriginText(), r.getOriginLat(), r.getOriginLng(),
                r.getDestinationText(), r.getDestinationLat(), r.getDestinationLng(),
                r.getDepartAt(), r.getSeatsTotal(), r.getSeatsAvailable(),
                r.getPricePerSeat(), r.getNotes(),
                r.getBagSize().name(), r.isSmokingAllowed(), r.isPetsAllowed(),
                r.getStatus().name(), r.getCreatedAt(),
                isDriver,
                myBooking == null ? null : myBooking.getId(),
                seats, seats,
                isDriver ? confirmed.stream().map(RiderSummary::from).toList() : List.of());
    }
}
