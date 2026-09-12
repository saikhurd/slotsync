package com.slotsync.integration;

import com.slotsync.dto.BookingDtos.CreateBookingRequest;
import com.slotsync.entity.BookingStatus;
import com.slotsync.entity.Resource;
import com.slotsync.repository.BookingRepository;
import com.slotsync.repository.ResourceRepository;
import com.slotsync.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The centerpiece test. A naive "check-then-insert" booking implementation
 * passes this test roughly 0% of the time under real concurrency — 20
 * threads racing the same read-then-write window will produce multiple
 * CONFIRMED rows. This test runs against a REAL Postgres container (not H2)
 * because part of the guarantee is enforced by a Postgres-specific partial
 * unique index; H2 would give false confidence that the code is correct.
 */
@Testcontainers
@SpringBootTest
class BookingConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("slotsync_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ResourceRepository resourceRepository;

    private Long resourceId;
    private LocalDateTime contestedSlot;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        resourceRepository.deleteAll();
        Resource resource = resourceRepository.save(new Resource("Conference Room A", "Concurrency test fixture"));
        resourceId = resource.getId();
        contestedSlot = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void twentyConcurrentRequestsForSameSlot_exactlyOneSucceeds() throws InterruptedException {
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        CreateBookingRequest request = new CreateBookingRequest(
                resourceId, contestedSlot, contestedSlot.plusHours(1));

        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            pool.submit(() -> {
                try {
                    startLine.await(); // all threads release at once
                    bookingService.createBooking(userId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startLine.countDown();
        finishLine.await();
        pool.shutdown();

        List<com.slotsync.entity.Booking> confirmed = bookingRepository
                .findByResourceIdAndStatusAndSlotStartBetween(
                        resourceId, BookingStatus.CONFIRMED,
                        contestedSlot.minusMinutes(1), contestedSlot.plusMinutes(1));

        assertThat(confirmed).hasSize(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }
}
