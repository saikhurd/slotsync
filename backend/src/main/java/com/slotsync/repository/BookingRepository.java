package com.slotsync.repository;

import com.slotsync.entity.Booking;
import com.slotsync.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderBySlotStartDesc(Long userId);

    boolean existsByResourceIdAndSlotStartAndStatus(Long resourceId, LocalDateTime slotStart, BookingStatus status);

    List<Booking> findByResourceIdAndStatusAndSlotStartBetween(
            Long resourceId, BookingStatus status, LocalDateTime from, LocalDateTime to);
}
