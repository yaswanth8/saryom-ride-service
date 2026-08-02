package com.saryom.rideservice.service;

import com.saryom.rideservice.domain.Booking;
import com.saryom.rideservice.domain.BookingRepository;
import com.saryom.rideservice.domain.BookingStatus;
import com.saryom.rideservice.domain.Ride;
import com.saryom.rideservice.domain.RideRepository;
import com.saryom.rideservice.domain.RideStatus;
import com.saryom.rideservice.error.ConflictException;
import com.saryom.rideservice.error.NotFoundException;
import com.saryom.rideservice.events.BookingCancelledEvent;
import com.saryom.rideservice.events.DomainEventPublisher;
import com.saryom.rideservice.events.RideBookedEvent;
import com.saryom.rideservice.events.RideCancelledEvent;
import com.saryom.rideservice.events.RidePostedEvent;
import com.saryom.rideservice.web.dto.BookSeatsRequest;
import com.saryom.rideservice.web.dto.CreateRideRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RideServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");
    private static final Instant DEPART = NOW.plusSeconds(86_400);

    private final RideRepository rides = mock(RideRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private RideService service;

    @BeforeEach
    void setUp() {
        service = new RideService(rides, bookings, events, clock);
        when(rides.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookings.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookings.findByRideIdAndStatus(any(), any())).thenReturn(List.of());
        when(bookings.findByRideIdAndRiderIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
    }

    private Ride ride(int seats) {
        return new Ride(UUID.randomUUID(), "driver-1", "Chicago", 41.87, -87.62,
                "Milwaukee", 43.04, -87.90, DEPART, seats, new BigDecimal("12.50"), null, NOW);
    }

    private void existing(Ride r) {
        when(rides.findById(r.getId())).thenReturn(Optional.of(r));
    }

    @Test
    void postingARideAnnouncesIt() {
        CreateRideRequest req = new CreateRideRequest("Chicago", 41.87, -87.62,
                "Milwaukee", 43.04, -87.90, DEPART, 3, new BigDecimal("12.50"), "No pets");
        service.create("driver-1", req);
        verify(events).publish(eq("ride.posted"), any(RidePostedEvent.class));
    }

    @Test
    void bookingTakesSeatsAndTellsTheDriver() {
        Ride r = ride(3);
        existing(r);

        var response = service.book(r.getId(), "rider-1", new BookSeatsRequest(2));

        assertThat(response.seats()).isEqualTo(2);
        assertThat(r.getSeatsAvailable()).isEqualTo(1);

        ArgumentCaptor<RideBookedEvent> captor = ArgumentCaptor.forClass(RideBookedEvent.class);
        verify(events).publish(eq("ride.booked"), captor.capture());
        // The driver must be on the event: they are who gets notified, and a
        // consumer should not have to call back here to find out who that is.
        assertThat(captor.getValue().driverId()).isEqualTo("driver-1");
        assertThat(captor.getValue().riderId()).isEqualTo("rider-1");
    }

    @Test
    void aDriverCannotBookTheirOwnRide() {
        Ride r = ride(3);
        existing(r);
        assertThatThrownBy(() -> service.book(r.getId(), "driver-1", new BookSeatsRequest(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("your own ride");
        assertThat(r.getSeatsAvailable()).isEqualTo(3);
    }

    @Test
    void aRiderCannotHoldTwoBookingsOnOneRide() {
        Ride r = ride(3);
        existing(r);
        when(bookings.findByRideIdAndRiderIdAndStatus(r.getId(), "rider-1", BookingStatus.CONFIRMED))
                .thenReturn(Optional.of(new Booking(UUID.randomUUID(), r.getId(), "rider-1", 1, NOW)));

        assertThatThrownBy(() -> service.book(r.getId(), "rider-1", new BookSeatsRequest(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already have a booking");
        // No seats consumed by the rejected attempt.
        assertThat(r.getSeatsAvailable()).isEqualTo(3);
    }

    @Test
    void cancellingABookingReturnsTheSeats() {
        Ride r = ride(3);
        existing(r);
        r.book(2, NOW);
        Booking b = new Booking(UUID.randomUUID(), r.getId(), "rider-1", 2, NOW);
        when(bookings.findById(b.getId())).thenReturn(Optional.of(b));

        service.cancelBooking(r.getId(), b.getId(), "rider-1");

        assertThat(r.getSeatsAvailable()).isEqualTo(3);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(events).publish(eq("ride.booking_cancelled"), any(BookingCancelledEvent.class));
    }

    @Test
    void theDriverCanAlsoCancelARidersBooking() {
        Ride r = ride(3);
        existing(r);
        r.book(1, NOW);
        Booking b = new Booking(UUID.randomUUID(), r.getId(), "rider-1", 1, NOW);
        when(bookings.findById(b.getId())).thenReturn(Optional.of(b));

        service.cancelBooking(r.getId(), b.getId(), "driver-1");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void anUnrelatedUserCannotCancelSomeoneElsesBooking() {
        Ride r = ride(3);
        existing(r);
        Booking b = new Booking(UUID.randomUUID(), r.getId(), "rider-1", 1, NOW);
        when(bookings.findById(b.getId())).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.cancelBooking(r.getId(), b.getId(), "stranger"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancellingAnAlreadyCancelledBookingIsRejected() {
        Ride r = ride(3);
        existing(r);
        Booking b = new Booking(UUID.randomUUID(), r.getId(), "rider-1", 1, NOW);
        b.cancel(NOW);
        when(bookings.findById(b.getId())).thenReturn(Optional.of(b));

        // Without this guard a double-cancel would release the seats twice.
        assertThatThrownBy(() -> service.cancelBooking(r.getId(), b.getId(), "rider-1"))
                .isInstanceOf(ConflictException.class);
        verify(events, never()).publish(eq("ride.booking_cancelled"), any());
    }

    @Test
    void aBookingFromAnotherRideIsNotFound() {
        Ride r = ride(3);
        existing(r);
        Booking other = new Booking(UUID.randomUUID(), UUID.randomUUID(), "rider-1", 1, NOW);
        when(bookings.findById(other.getId())).thenReturn(Optional.of(other));

        // Guards against cancelling a booking by id alone via the wrong ride.
        assertThatThrownBy(() -> service.cancelBooking(r.getId(), other.getId(), "rider-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancellingARideCancelsEveryBookingAndNamesTheStrandedRiders() {
        Ride r = ride(4);
        existing(r);
        Booking b1 = new Booking(UUID.randomUUID(), r.getId(), "rider-1", 1, NOW);
        Booking b2 = new Booking(UUID.randomUUID(), r.getId(), "rider-2", 2, NOW);
        when(bookings.findByRideIdAndStatus(r.getId(), BookingStatus.CONFIRMED))
                .thenReturn(List.of(b1, b2));

        service.cancelRide(r.getId(), "driver-1");

        assertThat(r.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(b1.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b2.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        ArgumentCaptor<RideCancelledEvent> captor = ArgumentCaptor.forClass(RideCancelledEvent.class);
        verify(events).publish(eq("ride.cancelled"), captor.capture());
        // Every affected rider must be named — they are about to be stranded,
        // and this service is the only place that knows who they are.
        assertThat(captor.getValue().affectedRiderIds()).containsExactlyInAnyOrder("rider-1", "rider-2");
    }

    @Test
    void onlyTheDriverCanCancelTheRide() {
        Ride r = ride(3);
        existing(r);
        assertThatThrownBy(() -> service.cancelRide(r.getId(), "rider-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnknownRideIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(rides.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDetail(missing, "rider-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ridesBookedByMeExcludesCancelledBookings() {
        Ride r = ride(3);
        Booking live = new Booking(UUID.randomUUID(), r.getId(), "rider-1", 1, NOW);
        Booking dead = new Booking(UUID.randomUUID(), UUID.randomUUID(), "rider-1", 1, NOW);
        dead.cancel(NOW);
        when(bookings.findByRiderIdOrderByCreatedAtDesc("rider-1")).thenReturn(List.of(live, dead));
        when(rides.findById(r.getId())).thenReturn(Optional.of(r));

        // A cancelled booking is not a travel plan and must not appear in "my trips".
        assertThat(service.bookedByMe("rider-1")).hasSize(1);
    }

    @Test
    void theDriverSeesTheRiderListButARiderDoesNot() {
        Ride r = ride(3);
        existing(r);
        when(bookings.findByRideIdAndStatus(r.getId(), BookingStatus.CONFIRMED))
                .thenReturn(List.of(new Booking(UUID.randomUUID(), r.getId(), "rider-1", 1, NOW)));

        assertThat(service.getDetail(r.getId(), "driver-1").riders()).hasSize(1);
        // A rider has no business seeing who else is in the car before the trip.
        assertThat(service.getDetail(r.getId(), "rider-2").riders()).isEmpty();
    }
}
