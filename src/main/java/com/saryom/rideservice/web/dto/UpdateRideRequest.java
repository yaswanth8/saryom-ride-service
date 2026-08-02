package com.saryom.rideservice.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Seat count is deliberately absent: reducing it below what riders have already
 * booked would silently strand someone. Changing capacity means cancelling the
 * ride and posting a new one, which at least tells the riders.
 */
public record UpdateRideRequest(
        @NotBlank @Size(max = 200) String originText,
        @NotBlank @Size(max = 200) String destinationText,
        @NotNull @Future Instant departAt,
        @NotNull @DecimalMin("0.00") BigDecimal pricePerSeat,
        @Size(max = 2000) String notes) {
}
