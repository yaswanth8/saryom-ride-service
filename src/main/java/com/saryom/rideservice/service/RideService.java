package com.saryom.rideservice.service;

import com.saryom.rideservice.domain.Booking;
import com.saryom.rideservice.domain.BookingRepository;
import com.saryom.rideservice.domain.BookingStatus;
import com.saryom.rideservice.domain.Haversine;
import com.saryom.rideservice.domain.Ride;
import com.saryom.rideservice.domain.RideRepository;
import com.saryom.rideservice.domain.RideSort;
import com.saryom.rideservice.error.ConflictException;
import com.saryom.rideservice.error.NotFoundException;
import com.saryom.rideservice.events.BookingCancelledEvent;
import com.saryom.rideservice.events.DomainEventPublisher;
import com.saryom.rideservice.events.RideBookedEvent;
import com.saryom.rideservice.events.RideCancelledEvent;
import com.saryom.rideservice.events.RidePostedEvent;
import com.saryom.rideservice.web.dto.BookSeatsRequest;
import com.saryom.rideservice.web.dto.BookingResponse;
import com.saryom.rideservice.web.dto.CreateRideRequest;
import com.saryom.rideservice.web.dto.RideCardResponse;
import com.saryom.rideservice.web.dto.RideDetailResponse;
import com.saryom.rideservice.web.dto.UpdateRideRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ride lifecycle, seat booking, and browse/search. */
@Service
public class RideService {

    private static final double DEFAULT_RADIUS_MILES = 50.0;

    private final RideRepository rides;
    private final BookingRepository bookings;
    private final DomainEventPublisher events;
    private final Clock clock;

    public RideService(RideRepository rides, BookingRepository bookings,
                       DomainEventPublisher events, Clock clock) {
        this.rides = rides;
        this.bookings = bookings;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Browse bookable rides. With coordinates, results are limited to a radius
     * of the pickup point and each card carries its distance; otherwise this is
     * a straight paged query.
     */
    @Transactional(readOnly = true)
    public Page<RideCardResponse> browse(String q, Integer seats, RideSort sort,
                                         Double lat, Double lng, Double radiusMiles,
                                         int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Instant now = clock.instant();
        String needle = blankToNull(q);

        if (lat != null && lng != null) {
            return browseByDistance(needle, seats, sort, lat, lng,
                    radiusMiles == null ? DEFAULT_RADIUS_MILES : radiusMiles, page, pageSize, now);
        }

        Pageable pageable = PageRequest.of(page, pageSize, sort.toSort());
        return rides.browse(needle, seats, now, pageable)
                .map(r -> RideCardResponse.from(r, null));
    }

    private Page<RideCardResponse> browseByDistance(String q, Integer seats, RideSort sort,
                                                    double lat, double lng, double radiusMiles,
                                                    int page, int pageSize, Instant now) {
        List<Ride> candidates = rides.browseWithCoordinates(q, seats, now);

        Map<UUID, Double> distances = candidates.stream().collect(Collectors.toMap(
                Ride::getId, r -> Haversine.miles(lat, lng, r.getOriginLat(), r.getOriginLng())));

        List<Ride> within = candidates.stream()
                .filter(r -> distances.get(r.getId()) <= radiusMiles)
                .sorted(comparator(sort, distances))
                .toList();

        int from = Math.min(page * pageSize, within.size());
        int to = Math.min(from + pageSize, within.size());
        List<RideCardResponse> content = within.subList(from, to).stream()
                .map(r -> RideCardResponse.from(r, round(distances.get(r.getId()))))
                .toList();

        return new PageImpl<>(content, PageRequest.of(page, pageSize), within.size());
    }

    private Comparator<Ride> comparator(RideSort sort, Map<UUID, Double> distances) {
        return switch (sort) {
            case NEAREST -> Comparator.comparingDouble(r -> distances.get(r.getId()));
            case CHEAPEST -> Comparator.comparing(Ride::getPricePerSeat);
            case DEPARTING_SOON -> Comparator.comparing(Ride::getDepartAt);
        };
    }

    @Transactional(readOnly = true)
    public RideDetailResponse getDetail(UUID id, String viewerId) {
        Ride ride = load(id);
        Booking mine = viewerId == null ? null
                : bookings.findByRideIdAndRiderIdAndStatus(id, viewerId, BookingStatus.CONFIRMED).orElse(null);
        List<Booking> confirmed = bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED);
        return RideDetailResponse.from(ride, viewerId, mine, confirmed);
    }

    @Transactional
    public RideDetailResponse create(String driverId, CreateRideRequest req) {
        Ride ride = new Ride(UUID.randomUUID(), driverId,
                req.originText(), req.originLat(), req.originLng(),
                req.destinationText(), req.destinationLat(), req.destinationLng(),
                req.departAt(), req.seatsTotal(), req.pricePerSeat(), req.notes(), clock.instant());
        Ride saved = rides.save(ride);
        events.publish("ride.posted", RidePostedEvent.of(saved.getId(), driverId,
                saved.getOriginText(), saved.getDestinationText(), saved.getDepartAt(), saved.getSeatsTotal()));
        return RideDetailResponse.from(saved, driverId, null, List.of());
    }

    @Transactional
    public RideDetailResponse update(UUID id, String uid, UpdateRideRequest req) {
        Ride ride = load(id);
        ride.requireDriver(uid);
        ride.updateDetails(req.originText(), req.destinationText(), req.departAt(),
                req.pricePerSeat(), req.notes(), clock.instant());
        Ride saved = rides.save(ride);
        return RideDetailResponse.from(saved, uid, null,
                bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED));
    }

    /**
     * Books seats for a rider.
     *
     * <p>The seat decrement is guarded by {@code @Version} on {@link Ride}: two
     * riders taking the last seat both pass the domain check, and the loser's
     * commit fails rather than overbooking the car. The controller maps that to
     * a 409 so the rider is told to pick another ride instead of turning up.
     */
    @Transactional
    public BookingResponse book(UUID rideId, String riderId, BookSeatsRequest req) {
        Ride ride = load(rideId);
        if (ride.isDrivenBy(riderId)) {
            throw new ConflictException("You cannot book a seat on your own ride");
        }
        if (bookings.findByRideIdAndRiderIdAndStatus(rideId, riderId, BookingStatus.CONFIRMED).isPresent()) {
            throw new ConflictException("You already have a booking on this ride");
        }

        Instant now = clock.instant();
        ride.book(req.seats(), now);
        rides.save(ride);

        Booking booking = bookings.save(new Booking(UUID.randomUUID(), rideId, riderId, req.seats(), now));
        events.publish("ride.booked", RideBookedEvent.of(rideId, booking.getId(),
                ride.getDriverId(), riderId, req.seats(), ride.getOriginText(), ride.getDestinationText()));
        return BookingResponse.from(booking);
    }

    /** Cancels a booking. Allowed for the rider who made it or the ride's driver. */
    @Transactional
    public BookingResponse cancelBooking(UUID rideId, UUID bookingId, String uid) {
        Ride ride = load(rideId);
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("No booking " + bookingId));
        if (!booking.getRideId().equals(rideId)) {
            throw new NotFoundException("No booking " + bookingId + " on ride " + rideId);
        }
        if (!booking.isHeldBy(uid) && !ride.isDrivenBy(uid)) {
            throw new AccessDeniedException("Only the rider or the driver can cancel this booking");
        }
        if (!booking.isConfirmed()) {
            throw new ConflictException("This booking is already cancelled");
        }

        Instant now = clock.instant();
        booking.cancel(now);
        bookings.save(booking);
        ride.releaseSeats(booking.getSeats(), now);
        rides.save(ride);

        events.publish("ride.booking_cancelled", BookingCancelledEvent.of(rideId, bookingId,
                ride.getDriverId(), booking.getRiderId(), booking.getSeats()));
        return BookingResponse.from(booking);
    }

    /**
     * The driver calls the trip off. Every confirmed booking is cancelled in the
     * same transaction, so no rider is left holding a seat on a ride that is not
     * happening.
     */
    @Transactional
    public void cancelRide(UUID id, String uid) {
        Ride ride = load(id);
        ride.cancel(uid, clock.instant());

        List<Booking> confirmed = bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED);
        Instant now = clock.instant();
        confirmed.forEach(b -> b.cancel(now));
        bookings.saveAll(confirmed);
        rides.save(ride);

        events.publish("ride.cancelled", RideCancelledEvent.of(id, ride.getDriverId(),
                confirmed.stream().map(Booking::getRiderId).toList(),
                ride.getOriginText(), ride.getDestinationText(), ride.getDepartAt()));
    }

    @Transactional
    public RideDetailResponse complete(UUID id, String uid) {
        Ride ride = load(id);
        ride.complete(uid, clock.instant());
        Ride saved = rides.save(ride);
        return RideDetailResponse.from(saved, uid, null,
                bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED));
    }

    @Transactional(readOnly = true)
    public List<RideCardResponse> drivenByMe(String uid) {
        return rides.findByDriverIdOrderByDepartAtDesc(uid).stream()
                .map(r -> RideCardResponse.from(r, null))
                .toList();
    }

    /** Rides the user has a live seat on. Cancelled bookings are not travel plans. */
    @Transactional(readOnly = true)
    public List<RideCardResponse> bookedByMe(String uid) {
        return bookings.findByRiderIdOrderByCreatedAtDesc(uid).stream()
                .filter(Booking::isConfirmed)
                .map(b -> rides.findById(b.getRideId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(r -> RideCardResponse.from(r, null))
                .toList();
    }

    private Ride load(UUID id) {
        return rides.findById(id).orElseThrow(() -> new NotFoundException("No ride " + id));
    }

    private static Double round(double miles) {
        return Math.round(miles * 10.0) / 10.0;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
