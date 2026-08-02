package com.saryom.rideservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByRideIdAndStatus(UUID rideId, BookingStatus status);

    List<Booking> findByRiderIdOrderByCreatedAtDesc(String riderId);

    /**
     * A rider's live booking on a ride. Used to stop the same person holding two
     * confirmed bookings on one trip — they should change the seat count on the
     * booking they already have.
     */
    Optional<Booking> findByRideIdAndRiderIdAndStatus(UUID rideId, String riderId, BookingStatus status);
}
