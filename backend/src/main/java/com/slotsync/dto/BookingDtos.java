package com.slotsync.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class BookingDtos {

    public record CreateBookingRequest(
            @NotNull Long resourceId,
            @NotNull @Future LocalDateTime slotStart,
            @NotNull @Future LocalDateTime slotEnd
    ) {}

    public record BookingResponse(
            Long id,
            Long resourceId,
            String resourceName,
            LocalDateTime slotStart,
            LocalDateTime slotEnd,
            String status
    ) {}
}
