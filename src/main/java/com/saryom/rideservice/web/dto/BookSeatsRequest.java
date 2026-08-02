package com.saryom.rideservice.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BookSeatsRequest(@Min(1) @Max(8) int seats) {
}
