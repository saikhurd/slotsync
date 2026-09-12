package com.slotsync.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalDateTime slotEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    /**
     * Optimistic-lock fast path: Hibernate stamps every UPDATE with
     * "WHERE id = ? AND version = ?". A concurrent writer that read the same
     * version loses the race and gets an ObjectOptimisticLockingFailureException,
     * which the service layer translates to HTTP 409 rather than letting the
     * write silently clobber another booking.
     */
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Booking(Long resourceId, Long userId, LocalDateTime slotStart, LocalDateTime slotEnd) {
        this.resourceId = resourceId;
        this.userId = userId;
        this.slotStart = slotStart;
        this.slotEnd = slotEnd;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
