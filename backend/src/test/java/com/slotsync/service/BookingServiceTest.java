package com.slotsync.service;

import com.slotsync.dto.BookingDtos.CreateBookingRequest;
import com.slotsync.entity.Booking;
import com.slotsync.entity.Resource;
import com.slotsync.exception.SlotConflictException;
import com.slotsync.repository.BookingRepository;
import com.slotsync.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private BookingService self; // stands in for the @Lazy self-injected proxy

    @InjectMocks
    private BookingService bookingService;

    private Resource activeResource;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() throws Exception {
        // @InjectMocks wires constructor args by type; "self" needs to be
        // swapped in explicitly since the real field is also a BookingService.
        Field selfField = BookingService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(bookingService, self);

        activeResource = new Resource("Room A", "desc");
        setId(activeResource, 1L);

        request = new CreateBookingRequest(1L, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1));
    }

    @Test
    void createBooking_happyPath_returnsConfirmedBooking() {
        Booking saved = new Booking(1L, 42L, request.slotStart(), request.slotEnd());
        setId(saved, 99L);

        when(self.attemptBooking(42L, request)).thenReturn(saved);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(activeResource));

        var response = bookingService.createBooking(42L, request);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.resourceName()).isEqualTo("Room A");
    }

    @Test
    void createBooking_persistentConflict_throwsSlotConflictAfterMaxRetries() {
        when(self.attemptBooking(eq(42L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, 1L));

        assertThatThrownBy(() -> bookingService.createBooking(42L, request))
                .isInstanceOf(SlotConflictException.class);

        // initial attempt + 3 retries = 4 calls
        verify(self, times(4)).attemptBooking(eq(42L), any());
    }

    @Test
    void createBooking_dbBackstopViolation_isTranslatedToConflict() {
        when(self.attemptBooking(eq(42L), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> bookingService.createBooking(42L, request))
                .isInstanceOf(SlotConflictException.class);
    }

    @Test
    void createBooking_recoversOnRetryIfSecondAttemptSucceeds() {
        Booking saved = new Booking(1L, 42L, request.slotStart(), request.slotEnd());
        setId(saved, 100L);

        when(self.attemptBooking(eq(42L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, 1L))
                .thenReturn(saved);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(activeResource));

        var response = bookingService.createBooking(42L, request);

        assertThat(response.id()).isEqualTo(100L);
        verify(self, times(2)).attemptBooking(eq(42L), any());
    }

    private static void setId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
