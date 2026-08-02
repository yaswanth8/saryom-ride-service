package com.saryom.rideservice.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateRideRequest(
        @NotBlank @Size(max = 200) String originText,
        Double originLat,
        Double originLng,
        @NotBlank @Size(max = 200) String destinationText,
        Double destinationLat,
        Double destinationLng,
        // A ride in the past cannot be booked, so it is rejected at the edge
        // rather than being accepted and then hidden from every query.
        @NotNull @Future Instant departAt,
        // Eight is the largest ordinary passenger vehicle; beyond that this is
        // a coach service and needs different rules.
        @Min(1) @Max(8) int seatsTotal,
        @NotNull @DecimalMin("0.00") BigDecimal pricePerSeat,
        @Size(max = 2000) String notes) {
}
