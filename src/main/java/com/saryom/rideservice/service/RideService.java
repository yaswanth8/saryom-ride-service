package com.saryom.rideservice.service;

import com.saryom.rideservice.domain.Booking;
import com.saryom.rideservice.domain.BookingRepository;
import com.saryom.rideservice.domain.BookingStatus;
import com.saryom.rideservice.domain.Haversine;
import com.saryom.rideservice.domain.Ride;
import com.saryom.rideservice.domain.RideRepository;
import com.saryom.rideservice.domain.RideSort;
import com.saryom.rideservice.domain.RideStatus;
import com.saryom.rideservice.error.ConflictException;
import com.saryom.rideservice.error.NotFoundException;
import com.saryom.rideservice.events.BookingCancelledEvent;
import com.saryom.rideservice.events.DomainEventPublisher;
import com.saryom.rideservice.events.RideBookedEvent;
import com.saryom.rideservice.events.RideCancelledEvent;
import com.saryom.rideservice.events.RideChangedEvent;
import com.saryom.rideservice.events.RideCompletedEvent;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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

    /**
     * Upper bound used when the caller gives none.
     *
     * <p>The query needs a concrete ceiling so the date window can be one plain
     * comparison rather than a nullable parameter — Hibernate cannot infer the
     * type of a null Instant, and the alternative is a CAST that has to be
     * repeated in every query. A year out is well past any real ride.
     */
    private static final Duration UNBOUNDED_WINDOW = Duration.ofDays(365);

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
                                         Instant departAfter, Instant departBefore,
                                         Double lat, Double lng, Double radiusMiles,
                                         int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Instant now = clock.instant();
        String needle = blankToNull(q);

        // A ride in the past is never bookable, so departAfter can only narrow
        // the window, never widen it back over rides that have already gone.
        Instant from = departAfter == null || departAfter.isBefore(now) ? now : departAfter;
        Instant until = departBefore == null ? now.plus(UNBOUNDED_WINDOW) : departBefore;
        if (!until.isAfter(from)) {
            // An inverted window matches nothing; say so with an empty page
            // rather than letting the database decide what a backwards range means.
            return Page.empty(PageRequest.of(page, pageSize));
        }

        if (lat != null && lng != null) {
            return browseByDistance(needle, seats, sort, lat, lng,
                    radiusMiles == null ? DEFAULT_RADIUS_MILES : radiusMiles,
                    page, pageSize, from, until);
        }

        Pageable pageable = PageRequest.of(page, pageSize, sort.toSort());
        return rides.browse(needle, seats, from, until, pageable)
                .map(r -> RideCardResponse.from(r, null));
    }

    private Page<RideCardResponse> browseByDistance(String q, Integer seats, RideSort sort,
                                                    double lat, double lng, double radiusMiles,
                                                    int page, int pageSize,
                                                    Instant windowStart, Instant windowEnd) {
        List<Ride> candidates = rides.browseWithCoordinates(q, seats, windowStart, windowEnd);

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
        // Only the driver is shown the rider list, so only the driver's request
        // should pay for it. Loading it unconditionally meant every anonymous
        // view of a shared ride link ran a query it then discarded — and public
        // detail views are the common case, not the rare one.
        List<Booking> confirmed = ride.isDrivenBy(viewerId)
                ? bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED)
                : List.of();
        return RideDetailResponse.from(ride, viewerId, mine, confirmed,
                rides.countByDriverIdAndStatus(ride.getDriverId(), RideStatus.COMPLETED));
    }

    @Transactional
    public RideDetailResponse create(String driverId, CreateRideRequest req) {
        Ride ride = new Ride(UUID.randomUUID(), driverId,
                req.originText(), req.originLat(), req.originLng(),
                req.destinationText(), req.destinationLat(), req.destinationLng(),
                req.departAt(), req.seatsTotal(), req.pricePerSeat(), req.notes(),
                req.bagSize(), req.smokingAllowedOrDefault(), req.petsAllowedOrDefault(), clock.instant());
        Ride saved = rides.save(ride);
        events.publish("ride.posted", RidePostedEvent.of(saved.getId(), driverId,
                saved.getOriginText(), saved.getDestinationText(), saved.getDepartAt(), saved.getSeatsTotal()));
        return RideDetailResponse.from(saved, driverId, null, List.of(),
                rides.countByDriverIdAndStatus(driverId, RideStatus.COMPLETED));
    }

    /**
     * Edits a ride, telling anyone already booked on it what changed.
     *
     * <p>The old departure and price are captured before the update because they
     * are the two edits that can strand a rider or cost them money, and the
     * announcement is worthless without the before-and-after. Cancelling a ride
     * has always notified its riders; changing it used to be silent, which is
     * worse — the rider still believes they have a plan.
     */
    @Transactional
    public RideDetailResponse update(UUID id, String uid, UpdateRideRequest req) {
        Ride ride = load(id);
        ride.requireDriver(uid);
        Instant oldDepartAt = ride.getDepartAt();
        BigDecimal oldPrice = ride.getPricePerSeat();

        ride.updateDetails(req.originText(), req.destinationText(), req.departAt(),
                req.pricePerSeat(), req.notes(),
                req.bagSize(), req.smokingAllowed(), req.petsAllowed(), clock.instant());
        Ride saved = rides.save(ride);

        List<Booking> confirmed = bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED);
        announceChange(saved, confirmed, oldDepartAt, oldPrice);

        return RideDetailResponse.from(saved, uid, null, confirmed,
                rides.countByDriverIdAndStatus(saved.getDriverId(), RideStatus.COMPLETED));
    }

    /**
     * Publishes {@code ride.changed} when the terms actually moved and there is
     * somebody to tell.
     *
     * <p>Silent on a ride nobody booked — there is no plan to disrupt — and
     * silent when only the notes or route text were reworded, because a push for
     * every reworded note is a push nobody reads.
     */
    private void announceChange(Ride ride, List<Booking> confirmed,
                                Instant oldDepartAt, BigDecimal oldPrice) {
        if (confirmed.isEmpty()) {
            return;
        }
        RideChangedEvent event = RideChangedEvent.of(ride.getId(), ride.getDriverId(),
                confirmed.stream().map(Booking::getRiderId).toList(),
                ride.getOriginText(), ride.getDestinationText(),
                oldDepartAt, ride.getDepartAt(), oldPrice, ride.getPricePerSeat());
        if (event.departureMoved() || event.priceChanged()) {
            events.publish("ride.changed", event);
        }
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
        List<Booking> confirmed = bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED);
        announceCompletion(saved, confirmed, true);
        return RideDetailResponse.from(saved, uid, null, confirmed,
                rides.countByDriverIdAndStatus(saved.getDriverId(), RideStatus.COMPLETED));
    }

    /**
     * Announces who actually travelled together.
     *
     * <p>Only riders holding a confirmed booking are named: someone who
     * cancelled never got in the car, and treating them as a participant would
     * let them review a driver they never met.
     */
    private void announceCompletion(Ride ride, List<Booking> confirmed, boolean driverClosed) {
        List<String> riderIds = confirmed.stream().map(Booking::getRiderId).distinct().toList();
        events.publish("ride.completed", RideCompletedEvent.of(
                ride.getId(), ride.getDriverId(), riderIds,
                ride.getOriginText(), ride.getDestinationText(),
                ride.getDepartAt(), driverClosed));
    }

    /**
     * Closes a ride whose departure has passed, so it stops showing as active
     * in the driver's and riders' own lists.
     *
     * <p>Its own transaction, so one failure inside a sweep cannot roll back
     * the rest of the batch.
     *
     * @return true when this call performed the close. False means someone got
     *     there first — the driver completed or cancelled it, or a concurrent
     *     sweep on another instance won. None of those is an error.
     */
    @Transactional
    public boolean closeDeparted(UUID id) {
        Ride ride = rides.findById(id).orElse(null);
        if (ride == null
                || (ride.getStatus() != RideStatus.OPEN && ride.getStatus() != RideStatus.FULL)) {
            return false;
        }
        ride.closeAfterDeparture(clock.instant());
        rides.save(ride);
        // A swept ride is still a trip that happened, so it announces itself the
        // same way — flagged as not driver-closed, since nobody asserted it ran.
        announceCompletion(ride, bookings.findByRideIdAndStatus(id, BookingStatus.CONFIRMED), false);
        return true;
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
        List<UUID> rideIds = bookings.findByRiderIdOrderByCreatedAtDesc(uid).stream()
                .filter(Booking::isConfirmed)
                .map(Booking::getRideId)
                .toList();
        if (rideIds.isEmpty()) {
            return List.of();
        }
        // One query for all of them. This was a findById per booking — a rider
        // with twenty trips paid twenty round trips, and on Neon each of those
        // crosses the network rather than staying on the box.
        Map<UUID, Ride> byId = rides.findAllById(rideIds).stream()
                .collect(Collectors.toMap(Ride::getId, r -> r));
        // findAllById does not preserve order, so the caller's "most recent
        // first" is restored from the booking order it was derived from.
        return rideIds.stream()
                .map(byId::get)
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
