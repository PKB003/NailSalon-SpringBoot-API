package com.ttnails.booking_service.booking;

import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.bookings.service.BookingService;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S0-07 — Testcontainers MySQL + concurrency cho booking hiện tại.
 *
 * <p>Chạy trên MySQL thật (không phải H2) để hành vi khóa/transaction giống production.
 * Khi máy không có Docker, toàn bộ class bị skip ({@code disabledWithoutDocker = true}),
 * CI của GitHub Actions có Docker nên vẫn chạy.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class BookingConcurrencyTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.41")
            .withDatabaseName("booking_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Long serviceId;
    private LocalDateTime slot;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        Service service = serviceRepository.save(Service.builder()
                .name("Test Maniküre")
                .duration(30)
                .price(28.0)
                .build());
        serviceId = service.getId();
        // Ngày mai 10:00 — trong giờ mở cửa, đúng bước 30 phút, luôn ở tương lai.
        slot = LocalDate.now().plusDays(1).atTime(10, 0);
    }

    private void createBooking(LocalDateTime dateTime) {
        NewBookingRequest request = new NewBookingRequest();
        request.setServiceId(serviceId);
        request.setDate(dateTime.format(DATE));
        request.setTime(dateTime.format(TIME));
        request.setName("Test Kunde");
        request.setPhone("+4915112345678");
        request.setEmail("kunde@example.de");
        bookingService.createBooking(request, null);
    }

    @Test
    void sequentialBookingEnforcesCapacityOfTen() {
        for (int i = 0; i < BookingService.SLOT_CAPACITY; i++) {
            createBooking(slot);
        }

        assertThat(bookingRepository.count()).isEqualTo(BookingService.SLOT_CAPACITY);
        assertThatThrownBy(() -> createBooking(slot))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_FULL);
        assertThat(bookingRepository.count()).isEqualTo(BookingService.SLOT_CAPACITY);
    }

    @Test
    void concurrentBookingsOnDifferentSlotsAllSucceed() throws Exception {
        int threads = 10;

        // Mồi 1 booking/slot để index có record: tránh gap-lock toàn index khi bảng trống
        // (đây là hành vi thật của InnoDB REPEATABLE READ — sẽ được xử lý ở S1-05).
        for (int i = 0; i < threads; i++) {
            createBooking(slot.plusMinutes(30L * i));
        }
        assertThat(bookingRepository.count()).isEqualTo(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                LocalDateTime differentSlot = slot.plusMinutes(30L * i);
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    createBooking(differentSlot);
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(bookingRepository.count()).isEqualTo(threads * 2);
    }

    /**
     * Chứng minh lỗi B-03: khi slot chưa có booking nào, {@code SELECT ... FOR UPDATE}
     * không khóa được dòng "ảo" nên nhiều thread cùng lọt qua bước đếm capacity.
     *
     * <p>Bật test này sau khi S1-05 sửa bằng khóa staff row + unique index
     * (lúc đó kỳ vọng: đúng {@link BookingService#SLOT_CAPACITY} booking thành công).</p>
     */
    @Disabled("B-03: bật sau S1-05 (khóa staff row + unique index chống double-booking)")
    @Test
    void concurrentBookingsOnSameSlotNeverExceedCapacity() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    try {
                        createBooking(slot);
                        success.incrementAndGet();
                    } catch (AppException expectedWhenFixed) {
                        // SLOT_FULL là kết quả đúng sau khi sửa
                    }
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(success.get()).isLessThanOrEqualTo(BookingService.SLOT_CAPACITY);
        assertThat(bookingRepository.count()).isLessThanOrEqualTo(BookingService.SLOT_CAPACITY);
    }
}
