package com.ttnails.booking_service.customers.service;

import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.common.constant.BookingStatus;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.customers.dto.CustomerDTO;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CustomerService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public CustomerService(UserRepository userRepository, BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    /** Giữ shape array cũ cho GET /customers (S1). */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<CustomerDTO> getAllCustomers() {
        List<CustomerDTO> result = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            result.add(toDto(user, bookingRepository.findByUserId(user.getId())));
        }
        return result;
    }

    /** S2-09: phân trang chuẩn PageResponse khi client gửi {@code page}. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<CustomerDTO> getCustomers(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "name"));
        Page<CustomerDTO> result = userRepository.findAll(pageable)
                .map(user -> toDto(user, bookingRepository.findByUserId(user.getId())));
        return PageResponse.from(result);
    }

    private CustomerDTO toDto(User user, List<Booking> bookings) {
        String letzterBesuch = bookings.stream()
                .map(b -> b.getStartAt() != null ? b.getStartAt() : b.getPreferredDateTime())
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(dt -> dt.format(DATE_FORMAT))
                .orElse(null);
        long noShows = bookings.stream()
                .filter(b -> BookingStatus.NO_SHOW.equals(b.getStatus()))
                .count();
        return new CustomerDTO(
                user.getId() != null ? user.getId().toString() : null,
                user.getName(),
                user.getPhone(),
                user.getEmail(),
                letzterBesuch,
                bookings.size(),
                noShows
        );
    }
}
