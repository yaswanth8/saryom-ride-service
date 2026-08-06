package com.saryom.rideservice.web;

import com.saryom.rideservice.auth.CurrentUser;
import com.saryom.rideservice.domain.RideSort;
import com.saryom.rideservice.service.RideService;
import com.saryom.rideservice.web.dto.BookSeatsRequest;
import com.saryom.rideservice.web.dto.BookingResponse;
import com.saryom.rideservice.web.dto.CreateRideRequest;
import com.saryom.rideservice.web.dto.RideCardResponse;
import com.saryom.rideservice.web.dto.RideDetailResponse;
import com.saryom.rideservice.web.dto.UpdateRideRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @GetMapping
    public Page<RideCardResponse> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer seats,
            @RequestParam(defaultValue = "DEPARTING_SOON") RideSort sort,
            // A date window is how people actually shop for a ride ("anything
            // Friday?"); sorting alone made them scroll past a week of results.
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant departAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant departBefore,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusMiles,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return rideService.browse(q, seats, sort, departAfter, departBefore,
                lat, lng, radiusMiles, page, size);
    }

    /** Rides the caller is driving. */
    @GetMapping("/mine")
    public List<RideCardResponse> mine() {
        return rideService.drivenByMe(CurrentUser.requireUid());
    }

    /** Rides the caller has a seat on. */
    @GetMapping("/booked")
    public List<RideCardResponse> booked() {
        return rideService.bookedByMe(CurrentUser.requireUid());
    }

    /**
     * Detail is readable signed-out so a shared link works, but the viewer id is
     * passed through — it decides whether the rider list is included and whether
     * the caller sees their own booking.
     */
    @GetMapping("/{id}")
    public RideDetailResponse detail(@PathVariable UUID id) {
        return rideService.getDetail(id, CurrentUser.uid());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RideDetailResponse create(@Valid @RequestBody CreateRideRequest req) {
        return rideService.create(CurrentUser.requireUid(), req);
    }

    @PatchMapping("/{id}")
    public RideDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRideRequest req) {
        return rideService.update(id, CurrentUser.requireUid(), req);
    }

    @PostMapping("/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@PathVariable UUID id, @Valid @RequestBody BookSeatsRequest req) {
        return rideService.book(id, CurrentUser.requireUid(), req);
    }

    @DeleteMapping("/{id}/bookings/{bookingId}")
    public BookingResponse cancelBooking(@PathVariable UUID id, @PathVariable UUID bookingId) {
        return rideService.cancelBooking(id, bookingId, CurrentUser.requireUid());
    }

    @PostMapping("/{id}/complete")
    public RideDetailResponse complete(@PathVariable UUID id) {
        return rideService.complete(id, CurrentUser.requireUid());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        rideService.cancelRide(id, CurrentUser.requireUid());
    }
}
