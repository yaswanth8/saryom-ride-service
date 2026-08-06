package com.saryom.rideservice.service;

import com.saryom.rideservice.domain.BagSize;
import com.saryom.rideservice.domain.Ride;
import com.saryom.rideservice.domain.RideRepository;
import com.saryom.rideservice.domain.RideStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepartedRideSweeperTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private final RideRepository rides = mock(RideRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private RideService service;
    private DepartedRideSweeper sweeper;

    @BeforeEach
    void setUp() {
        service = new RideService(rides, mock(com.saryom.rideservice.domain.BookingRepository.class),
                mock(com.saryom.rideservice.events.DomainEventPublisher.class), clock);
        sweeper = new DepartedRideSweeper(rides, service, clock, 24);
        when(rides.save(any(Ride.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Ride ride(Instant departAt) {
        return new Ride(UUID.randomUUID(), "driver-1", "Chicago", null, null,
                "Milwaukee", null, null, departAt, 3, new BigDecimal("10.00"), null,
                BagSize.SMALL, false, false, departAt.minusSeconds(86_400));
    }

    private void staleRides(Ride... found) {
        Page<Ride> page = new PageImpl<>(List.of(found));
        when(rides.findByStatusInAndDepartAtBeforeOrderByDepartAtAsc(any(), any(), any()))
                .thenReturn(page);
        for (Ride r : found) {
            when(rides.findById(r.getId())).thenReturn(Optional.of(r));
        }
    }

    @Test
    void closesARideLeftOpenLongAfterDeparture() {
        Ride r = ride(NOW.minusSeconds(2 * 86_400));
        staleRides(r);

        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    void closesAFullRideToo() {
        // A full ride is just as stuck: nobody can book it and the driver has
        // no reason to revisit it.
        Ride r = ride(NOW.minusSeconds(2 * 86_400));
        r.book(3, r.getDepartAt().minusSeconds(3600));
        assertThat(r.getStatus()).isEqualTo(RideStatus.FULL);
        staleRides(r);

        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    void leavesARideThatIsAlreadyFinished() {
        Ride r = ride(NOW.minusSeconds(2 * 86_400));
        r.cancel("driver-1", NOW);
        staleRides(r);

        // Re-checked inside the transaction, so a concurrent sweeper or a
        // driver who acted first does not get double-processed.
        assertThat(sweeper.sweep()).isZero();
        assertThat(r.getStatus()).isEqualTo(RideStatus.CANCELLED);
    }

    @Test
    void reportsNothingWhenThereIsNothingToClose() {
        when(rides.findByStatusInAndDepartAtBeforeOrderByDepartAtAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(sweeper.sweep()).isZero();
    }

    @Test
    void oneBadRideDoesNotAbandonTheBatch() {
        Ride bad = ride(NOW.minusSeconds(2 * 86_400));
        Ride good = ride(NOW.minusSeconds(3 * 86_400));
        staleRides(bad, good);
        when(rides.findById(bad.getId())).thenThrow(new IllegalStateException("boom"));

        // The next sweep retries the failure; losing the whole batch to one row
        // would mean a single poisoned ride blocks every other close forever.
        assertThat(sweeper.sweep()).isEqualTo(1);
        assertThat(good.getStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    void closingIsIdempotent() {
        Ride r = ride(NOW.minusSeconds(2 * 86_400));
        staleRides(r);

        assertThat(service.closeDeparted(r.getId())).isTrue();
        assertThat(service.closeDeparted(r.getId())).isFalse();
    }
}
