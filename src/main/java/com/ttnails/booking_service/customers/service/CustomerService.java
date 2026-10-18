package com.ttnails.booking_service.customers.service;

import com.ttnails.booking_service.bookings.entity.Booking;
import com.ttnails.booking_service.bookings.repository.BookingRepository;
import com.ttnails.booking_service.customers.dto.CustomerDTO;
import com.ttnails.booking_service.users.entity.User;
import com.ttnails.booking_service.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CustomerService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerDTO> getAllCustomers() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        List<CustomerDTO> result = new ArrayList<>();

        for (User user : userRepository.findAll()) {
            List<Booking> bookings = bookingRepository.findByUserId(user.getId());

            String letzterBesuch = bookings.stream()
                    .map(Booking::getPreferredDateTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .map(dt -> dt.format(fmt))
                    .orElse(null);

            result.add(new CustomerDTO(
                    user.getId().toString(),
                    user.getName(),
                    user.getPhone(),
                    user.getEmail(),
                    letzterBesuch,
                    bookings.size()
            ));
        }
        return result;
    }
}
