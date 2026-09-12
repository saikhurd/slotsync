package com.slotsync.service;

import com.slotsync.dto.BookingDtos.BookingResponse;
import com.slotsync.dto.BookingDtos.CreateBookingRequest;
import com.slotsync.entity.Booking;
import com.slotsync.entity.BookingStatus;
import com.slotsync.entity.Resource;
import com.slotsync.exception.ResourceNotFoundException;
import com.slotsync.exception.SlotConflictException;
import com.slotsync.repository.BookingRepository;
import com.slotsync.repository.ResourceRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Concurrency strategy (see /decisions.md):
 *
 *  1. Fast path — JPA optimistic locking (@Version on Booking). Two requests
 *     racing for the same slot both read version N; whichever commits first
 *     wins, the loser gets ObjectOptimisticLockingFailureException.
 *  2. Backstop — a Postgres partial unique index on (resource_id, slot_start)
 *     WHERE status = 'CONFIRMED' catches any case the application-level check
 *     misses (e.g. two brand-new rows, which optimistic locking on an existing
 *     row can't protect against), raising DataIntegrityViolationException.
 *  3. Bounded retry — a transient loss of the fast-path race is retried a
 *     small number of times before giving up, since the *slot* might still be
 *     free even though a specific attempt collided.
 *
 * IMPORTANT: retries call back into `self.attemptBooking(...)`, not
 * `this.attemptBooking(...)`. Calling `this.method()` directly bypasses
 * Spring's transactional proxy entirely, so @Transactional would silently not
 * apply to the retried call. @Lazy self-injection routes the call back
 * through the proxy so each retry gets its own fresh transaction.
 */
@Service
public class BookingService {

    private static final int MAX_RETRIES = 3;

    private final BookingRepository bookingRepository;
    private final ResourceRepository resourceRepository;
    private final BookingService self;

    public BookingService(BookingRepository bookingRepository,
                           ResourceRepository resourceRepository,
                           @Lazy BookingService self) {
        this.bookingRepository = bookingRepository;
        this.resourceRepository = resourceRepository;
        this.self = self;
    }

    public BookingResponse createBooking(Long userId, CreateBookingRequest request) {
        Booking result = createWithRetry(userId, request, 0);
        Resource resource = resourceRepository.findById(result.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        return toResponse(result, resource.getName());
    }

    private Booking createWithRetry(Long userId, CreateBookingRequest request, int attempt) {
        try {
            return self.attemptBooking(userId, request);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
            if (attempt >= MAX_RETRIES) {
                throw new SlotConflictException(
                        "This slot was taken by another request. Please choose a different time.");
            }
            return createWithRetry(userId, request, attempt + 1);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking attemptBooking(Long userId, CreateBookingRequest request) {
        Resource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        if (!resource.isActive()) {
            throw new IllegalArgumentException("This resource is not currently bookable.");
        }
        if (!request.slotEnd().isAfter(request.slotStart())) {
            throw new IllegalArgumentException("slotEnd must be after slotStart.");
        }

        boolean alreadyTaken = bookingRepository.existsByResourceIdAndSlotStartAndStatus(
                request.resourceId(), request.slotStart(), BookingStatus.CONFIRMED);
        if (alreadyTaken) {
            throw new SlotConflictException("Slot already booked.");
        }

        Booking booking = new Booking(request.resourceId(), userId, request.slotStart(), request.slotEnd());
        // flush forces the INSERT (and the partial unique index check) to run
        // inside this method, so DataIntegrityViolationException surfaces here
        // and is caught by the retry loop rather than escaping later.
        return bookingRepository.saveAndFlush(booking);
    }

    @Transactional
    public void cancelBooking(Long userId, Long bookingId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!isAdmin && !booking.getUserId().equals(userId)) {
            throw new IllegalArgumentException("You can only cancel your own bookings.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

        @Transactional(readOnly = true)
        public List<BookingResponse> getMyBookings(Long userId) {
            List<Booking> bookings = bookingRepository.findByUserIdOrderBySlotStartDesc(userId);

            List<Long> resourceIds = 
            bookings.stream().map(Booking::getResourceId).distinct().toList();
            Map<Long, String> resourceNames =       
            resourceRepository.findAllById(resourceIds).stream()
                    .collect(Collectors.toMap(Resource::getId, Resource::getName));

            return bookings.stream()
                    .map(b -> toResponse(b, resourceNames.getOrDefault(b.getResourceId(),  
                    "Unknown resource")))
                    .toList();
    }

    private BookingResponse toResponse(Booking booking, String resourceName) {
        return new BookingResponse(
                booking.getId(),
                booking.getResourceId(),
                resourceName,
                booking.getSlotStart(),
                booking.getSlotEnd(),
                booking.getStatus().name()
        );
    }
}
