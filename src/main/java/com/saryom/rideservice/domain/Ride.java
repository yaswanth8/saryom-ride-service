package com.saryom.rideservice.domain;

import com.saryom.rideservice.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A seat-sharing offer for one trip. Aggregate root: seat arithmetic and the
 * open/full/completed/cancelled lifecycle live here, so no caller can move a
 * ride into a state that does not make sense.
 */
@Entity
@Table(name = "ride")
public class Ride {

    @Id
    private UUID id;

    /**
     * Guards seat arithmetic against lost updates.
     *
     * <p>Two riders booking the last seat both read {@code seatsAvailable = 1}
     * and both pass the guard in {@link #book}. Without a version the second
     * write silently overwrites the first and the car is overbooked — the
     * driver finds out at the kerb. With it the loser's commit fails and the
     * service turns that into a 409.
     *
     * <p>This is the same defect that was fixed in food-service reservations;
     * it is worse here, because a seat cannot be split.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "origin_text", nullable = false)
    private String originText;

    @Column(name = "origin_lat")
    private Double originLat;

    @Column(name = "origin_lng")
    private Double originLng;

    @Column(name = "destination_text", nullable = false)
    private String destinationText;

    @Column(name = "destination_lat")
    private Double destinationLat;

    @Column(name = "destination_lng")
    private Double destinationLng;

    @Column(name = "depart_at", nullable = false)
    private Instant departAt;

    @Column(name = "seats_total", nullable = false)
    private int seatsTotal;

    @Column(name = "seats_available", nullable = false)
    private int seatsAvailable;

    /** Contribution per seat. Zero is allowed — plenty of trips are offered free. */
    @Column(name = "price_per_seat", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(length = 2000)
    private String notes;

    /**
     * Trip preferences: the questions a rider would otherwise have to ask in a
     * message before booking. Non-null so a card can always render them —
     * "unspecified" would just push the question back into the notes field,
     * which is where these already were and why they were unusable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bag_size", nullable = false)
    private BagSize bagSize;

    @Column(name = "smoking_allowed", nullable = false)
    private boolean smokingAllowed;

    @Column(name = "pets_allowed", nullable = false)
    private boolean petsAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Ride() {
        // for JPA
    }

    public Ride(UUID id, String driverId, String originText, Double originLat, Double originLng,
                String destinationText, Double destinationLat, Double destinationLng,
                Instant departAt, int seatsTotal, BigDecimal pricePerSeat, String notes,
                BagSize bagSize, boolean smokingAllowed, boolean petsAllowed, Instant now) {
        this.id = id;
        this.driverId = driverId;
        this.originText = originText;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destinationText = destinationText;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.departAt = departAt;
        this.seatsTotal = seatsTotal;
        this.seatsAvailable = seatsTotal;
        this.pricePerSeat = pricePerSeat;
        this.notes = notes;
        // Null-tolerant so an older client that does not send preferences still
        // creates a valid ride rather than a 500.
        this.bagSize = bagSize == null ? BagSize.SMALL : bagSize;
        this.smokingAllowed = smokingAllowed;
        this.petsAllowed = petsAllowed;
        this.status = RideStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Takes {@code seats} off the ride.
     *
     * @throws ConflictException when the ride is not bookable or too few seats
     *     remain. Checked here rather than in the service so every caller gets
     *     the same rule.
     */
    public void book(int seats, Instant now) {
        if (seats < 1) {
            throw new ConflictException("A booking needs at least one seat");
        }
        if (status != RideStatus.OPEN) {
            throw new ConflictException("This ride is no longer accepting bookings");
        }
        if (departAt.isBefore(now)) {
            throw new ConflictException("This ride has already departed");
        }
        if (seats > seatsAvailable) {
            throw new ConflictException(
                    "Only " + seatsAvailable + " seat(s) left on this ride");
        }
        seatsAvailable -= seats;
        // FULL is derived, never set by a caller: it is simply "no seats left".
        if (seatsAvailable == 0) {
            status = RideStatus.FULL;
        }
        updatedAt = now;
    }

    /** Returns seats to the pool when a rider cancels or the driver calls the trip off. */
    public void releaseSeats(int seats, Instant now) {
        if (status == RideStatus.CANCELLED || status == RideStatus.COMPLETED) {
            // Nothing to reopen — the ride is over either way.
            return;
        }
        seatsAvailable = Math.min(seatsTotal, seatsAvailable + seats);
        if (seatsAvailable > 0 && status == RideStatus.FULL) {
            status = RideStatus.OPEN;
        }
        updatedAt = now;
    }

    public void cancel(String uid, Instant now) {
        requireDriver(uid);
        if (status == RideStatus.COMPLETED) {
            throw new ConflictException("A completed ride cannot be cancelled");
        }
        status = RideStatus.CANCELLED;
        updatedAt = now;
    }

    public void complete(String uid, Instant now) {
        requireDriver(uid);
        if (status == RideStatus.CANCELLED) {
            throw new ConflictException("A cancelled ride cannot be completed");
        }
        status = RideStatus.COMPLETED;
        updatedAt = now;
    }

    /**
     * Ends a ride the driver never closed themselves.
     *
     * <p>Separate from {@link #complete} because that asserts a driver said the
     * trip happened. This only asserts the departure time has passed and nobody
     * acted, which is a weaker claim — so it takes no uid and performs no
     * ownership check, and callers must not use it to bypass one.
     */
    public void closeAfterDeparture(Instant now) {
        if (status != RideStatus.OPEN && status != RideStatus.FULL) {
            return;
        }
        status = RideStatus.COMPLETED;
        updatedAt = now;
    }

    public void updateDetails(String originText, String destinationText, Instant departAt,
                              BigDecimal pricePerSeat, String notes,
                              BagSize bagSize, Boolean smokingAllowed, Boolean petsAllowed,
                              Instant now) {
        this.originText = originText;
        this.destinationText = destinationText;
        this.departAt = departAt;
        this.pricePerSeat = pricePerSeat;
        this.notes = notes;
        // Preferences are patch-style: an omitted field keeps its current value,
        // so a client that predates them cannot silently reset a driver's rules.
        if (bagSize != null) {
            this.bagSize = bagSize;
        }
        if (smokingAllowed != null) {
            this.smokingAllowed = smokingAllowed;
        }
        if (petsAllowed != null) {
            this.petsAllowed = petsAllowed;
        }
        this.updatedAt = now;
    }

    public void requireDriver(String uid) {
        if (!isDrivenBy(uid)) {
            throw new AccessDeniedException("Only the driver can change this ride");
        }
    }

    public boolean isDrivenBy(String uid) {
        return driverId.equals(uid);
    }

    /** Browsable = open, in the future, and with a seat to sell. */
    public boolean isBookable(Instant now) {
        return status == RideStatus.OPEN && seatsAvailable > 0 && departAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getOriginText() {
        return originText;
    }

    public Double getOriginLat() {
        return originLat;
    }

    public Double getOriginLng() {
        return originLng;
    }

    public String getDestinationText() {
        return destinationText;
    }

    public Double getDestinationLat() {
        return destinationLat;
    }

    public Double getDestinationLng() {
        return destinationLng;
    }

    public Instant getDepartAt() {
        return departAt;
    }

    public int getSeatsTotal() {
        return seatsTotal;
    }

    public int getSeatsAvailable() {
        return seatsAvailable;
    }

    public BigDecimal getPricePerSeat() {
        return pricePerSeat;
    }

    public String getNotes() {
        return notes;
    }

    public BagSize getBagSize() {
        return bagSize;
    }

    public boolean isSmokingAllowed() {
        return smokingAllowed;
    }

    public boolean isPetsAllowed() {
        return petsAllowed;
    }

    public RideStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
