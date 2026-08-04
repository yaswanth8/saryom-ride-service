package com.saryom.rideservice.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    /**
     * Bookable rides, optionally narrowed by a case-insensitive match on either
     * endpoint. Searching both origin and destination matters: "Chicago" should
     * find rides leaving there and rides heading there.
     */
    @Query("""
            SELECT r FROM Ride r
            WHERE r.status = com.saryom.rideservice.domain.RideStatus.OPEN
              AND r.seatsAvailable > 0
              AND r.departAt > :now
              AND (:q IS NULL
                   OR LOWER(r.originText) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(r.destinationText) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:seats IS NULL OR r.seatsAvailable >= :seats)
            """)
    Page<Ride> browse(@Param("q") String q, @Param("seats") Integer seats,
                      @Param("now") Instant now, Pageable pageable);

    /** Same filter, unpaged — the geo path sorts and paginates by distance in memory. */
    @Query("""
            SELECT r FROM Ride r
            WHERE r.status = com.saryom.rideservice.domain.RideStatus.OPEN
              AND r.seatsAvailable > 0
              AND r.departAt > :now
              AND (:q IS NULL
                   OR LOWER(r.originText) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(r.destinationText) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:seats IS NULL OR r.seatsAvailable >= :seats)
              AND r.originLat IS NOT NULL AND r.originLng IS NOT NULL
            """)
    List<Ride> browseWithCoordinates(@Param("q") String q, @Param("seats") Integer seats,
                                     @Param("now") Instant now);

    List<Ride> findByDriverIdOrderByDepartAtDesc(String driverId);

    /**
     * Rides still open or full whose departure has passed, oldest first.
     *
     * <p>Paged so one sweep cannot pull an unbounded backlog into memory — a
     * long outage would otherwise be drained in a single enormous transaction.
     */
    Page<Ride> findByStatusInAndDepartAtBeforeOrderByDepartAtAsc(
            Collection<RideStatus> statuses, Instant departedBefore, Pageable pageable);
}
