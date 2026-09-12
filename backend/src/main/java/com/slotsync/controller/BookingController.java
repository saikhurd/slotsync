package com.slotsync.controller;

import com.slotsync.dto.BookingDtos.BookingResponse;
import com.slotsync.dto.BookingDtos.CreateBookingRequest;
import com.slotsync.entity.User;
import com.slotsync.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request,
                                                   Authentication auth) {
        Long userId = currentUserId(auth);
        BookingResponse response = bookingService.createBooking(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<BookingResponse>> myBookings(Authentication auth) {
        return ResponseEntity.ok(bookingService.getMyBookings(currentUserId(auth)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        bookingService.cancelBooking(currentUserId(auth), id, isAdmin);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication auth) {
        return ((User) auth.getPrincipal()).getId();
    }
}
