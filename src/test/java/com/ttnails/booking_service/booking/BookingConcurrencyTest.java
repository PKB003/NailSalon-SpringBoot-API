package com.ttnails.booking_service.booking;

import com.ttnails.booking_service.bookings.dto.NewBookingRequest;
import com.ttnails.booking_service.bookings.repository.BookingItemRepository;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.bookings.service.BookingService;
import com.ttnails.booking_service.common.enums.SkillLevel;
import com.ttnails.booking_service.common.exception.AppException;
import com.ttnails.booking_service.common.exception.ErrorCode;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import com.ttnails.booking_service.staff.entity.Staff;
import com.ttnails.booking_service.staff.entity.StaffSkill;
import com.ttnails.booking_service.staff.repository.StaffRepository;
import com.ttnails.booking_service.staff.repository.StaffSkillRepository;
import com.ttnails.booking_service.support.TestSupport;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1-05 — chống double-booking trên MySQL thật (Testcontainers).
 *
 * <p>Kỳ vọng sau khi khóa staff row + unique index {@code uk_booking_staff_start}:</p>
 * <ul>
 *   <li>20 thread cùng thợ + cùng slot → đúng <b>1</b> thành công, 19 nhận 409 {@code SLOT_TAKEN}.</li>
 *   <li>Nhiều thợ: số booking thành công = số thợ rảnh (không vượt).</li>
 *   <li>Các slot khác nhau trên cùng thợ đều thành công (khóa không chặn nhầm).</li>
 * </ul>
 *
 * <p>Khi máy không có Docker, class bị skip ({@code disabledWithoutDocker = true}).</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class BookingConcurrencyTest {

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
    BookingService bookingService;
    @Autowired
    ServiceRepository serviceRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    BookingItemRepository bookingItemRepository;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StaffSkillRepository staffSkillRepository;

    Long serviceId;
    List<Long> staffIds;
    LocalDateTime slot;

    @BeforeEach
    void setUp() {
        bookingItemRepository.deleteAll();
        bookingRepository.deleteAll();

        Service service = serviceRepository.save(Service.builder()
                .name("Concurrency Service " + System.nanoTime())
                .duration(30)
                .bufferMinutes(10)
                .price(28.0)
                .build());
        serviceId = service.getId();

        List<Staff> staff = staffRepository.findByActiveTrueOrderByIdAsc();
        staffIds = staff.stream().map(Staff::getId).toList();
        // Cấp skill cho dịch vụ test để thợ seed nhận được lịch
        for (Staff member : staff) {
            boolean has = staffSkillRepository.findByStaffId(member.getId()).stream()
                    .anyMatch(ss -> ss.getService().getId().equals(serviceId));
            if (!has) {
                staffSkillRepository.save(StaffSkill.builder()
                        .staff(member).service(service).level(SkillLevel.ADVANCED).build());
            }
        }
        assertThat(staffIds).hasSizeGreaterThanOrEqualTo(3);

        // Ngày mai (bỏ Chủ nhật) 10:00 — trong ca seed 09:00–18:00, đúng bước 30'.
        LocalDate date = TestSupport.workdayAfter(1);
        slot = date.atTime(10, 0);
    }

    private void createBooking(LocalDateTime dateTime, Long staffId) {
        NewBookingRequest request = new NewBookingRequest();
        request.setServiceIds(List.of(serviceId));
        request.setStaffId(staffId);
        request.setDate(dateTime.toLocalDate().format(TestSupport.DATE));
        request.setTime(dateTime.toLocalTime().format(TestSupport.TIME));
        request.setName("Test Kunde");
        request.setPhone("+4915112345678");
        request.setEmail("kunde@example.de");
        bookingService.createBooking(request, null);
    }

    @Test
    void twentyThreadsOnSameStaffAndSlotOnlyOneSucceeds() throws Exception {
        int threads = 20;
        Long staffId = staffIds.get(0);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger slotTaken = new AtomicInteger();
        Set<String> unexpected = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    try {
                        createBooking(slot, staffId);
                        success.incrementAndGet();
                    } catch (AppException ex) {
                        if (ex.getErrorCode() == ErrorCode.SLOT_TAKEN) {
                            slotTaken.incrementAndGet();
                        } else {
                            unexpected.add(ex.getErrorCode().name() + ": " + ex.getMessage());
                        }
                    } catch (Exception ex) {
                        unexpected.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).as("chỉ đúng 1 booking được tạo").isEqualTo(1);
        assertThat(slotTaken.get()).isEqualTo(threads - 1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingItemRepository.count()).isEqualTo(1);
    }

    @Test
    void manyStaffSameSlotOnlyAsManyAsFreeStaffSucceed() throws Exception {
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger slotTaken = new AtomicInteger();
        Set<String> unexpected = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    try {
                        // staffId = null → hệ thống tự chọn thợ ít việc nhất
                        createBooking(slot, null);
                        success.incrementAndGet();
                    } catch (AppException ex) {
                        if (ex.getErrorCode() == ErrorCode.SLOT_TAKEN) {
                            slotTaken.incrementAndGet();
                        } else {
                            unexpected.add(ex.getErrorCode().name() + ": " + ex.getMessage());
                        }
                    } catch (Exception ex) {
                        unexpected.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).as("mỗi thợ chỉ nhận 1 booking").isEqualTo(staffIds.size());
        assertThat(slotTaken.get()).isEqualTo(threads - staffIds.size());
        assertThat(bookingRepository.count()).isEqualTo(staffIds.size());

        // Mọi booking đều rơi vào thợ khác nhau
        assertThat(bookingRepository.findAll().stream()
                .map(b -> b.getStaff().getId())
                .collect(Collectors.toSet()))
                .hasSize(staffIds.size());
    }

    @Test
    void differentSlotsOnSameStaffAllSucceed() throws Exception {
        int threads = 8;
        Long staffId = staffIds.get(0);
        LocalDateTime firstSlot = slot.withHour(9).withMinute(0);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        Set<String> unexpected = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                // Cách nhau 60' để không chồng buffer (30' + 10')
                LocalDateTime differentSlot = firstSlot.plusMinutes(60L * i);
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    try {
                        createBooking(differentSlot, staffId);
                        success.incrementAndGet();
                    } catch (Exception ex) {
                        unexpected.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(threads);
        assertThat(bookingRepository.count()).isEqualTo(threads);
    }

    @Test
    void multiServiceBookingReservesWholeSpan() {
        Long staffId = staffIds.get(0);
        Service second = serviceRepository.save(Service.builder()
                .name("Concurrency Second " + System.nanoTime())
                .duration(45)
                .bufferMinutes(10)
                .price(30.0)
                .build());
        staffSkillRepository.save(StaffSkill.builder()
                .staff(staffRepository.findById(staffId).orElseThrow())
                .service(second).level(SkillLevel.ADVANCED).build());

        NewBookingRequest request = new NewBookingRequest();
        request.setServiceIds(List.of(serviceId, second.getId()));
        request.setStaffId(staffId);
        request.setDate(slot.toLocalDate().format(TestSupport.DATE));
        request.setTime(slot.toLocalTime().format(TestSupport.TIME));
        request.setName("Multi Service");
        request.setPhone("+4915112345678");
        request.setEmail("multi@example.de");
        bookingService.createBooking(request, null);

        var booking = bookingRepository.findAll().get(0);
        // 30' + 45' = 75' (end), buffer 2 × 10' = 95' (buffer_end)
        assertThat(booking.getEndAt()).isEqualTo(slot.plusMinutes(75));
        assertThat(booking.getBufferEndAt()).isEqualTo(slot.plusMinutes(95));

        // Slot 10:30 bị chặn vì buffer_end = 11:35
        NewBookingRequest overlapping = new NewBookingRequest();
        overlapping.setServiceIds(List.of(second.getId()));
        overlapping.setStaffId(staffId);
        overlapping.setDate(slot.toLocalDate().format(TestSupport.DATE));
        overlapping.setTime("10:30");
        overlapping.setName("Overlap");
        overlapping.setPhone("+4915112345678");
        overlapping.setEmail("overlap@example.de");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> bookingService.createBooking(overlapping, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_TAKEN);
    }
}
