package com.saryom.rideservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * One rider's claim on seats in a ride.
 *
 * <p>Kept as its own entity rather than a seat counter on {@link Ride} because
 * the ride needs to know <em>who</em> is coming: the driver has to recognise
 * people at the pickup, and a rider needs their own trips listed back to them.
 * A bare count could do neither.
 *
 * <p>Cancelling sets a status rather than deleting the row, so a rider who
 * books, cancels and rebooks leaves a history the driver can see.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Column(nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Booking() {
        // for JPA
    }

    public Booking(UUID id, UUID rideId, String riderId, int seats, Instant now) {
        this.id = id;
        this.rideId = rideId;
        this.riderId = riderId;
        this.seats = seats;
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = now;
    }

    public void cancel(Instant now) {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    public boolean isHeldBy(String uid) {
        return riderId.equals(uid);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public String getRiderId() {
        return riderId;
    }

    public int getSeats() {
        return seats;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
