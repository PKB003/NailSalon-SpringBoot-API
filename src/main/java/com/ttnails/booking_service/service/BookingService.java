package com.ttnails.booking_service.service;


import com.ttnails.booking_service.dto.BookingDTO;
import com.ttnails.booking_service.dto.GuestBookingRequest;
import com.ttnails.booking_service.dto.UserBookingRequest;
import com.ttnails.booking_service.entity.Booking;
import com.ttnails.booking_service.entity.User;
import com.ttnails.booking_service.exception.AppException;
import com.ttnails.booking_service.exception.ErrorCode;
import com.ttnails.booking_service.exception.NotFoundException;
import com.ttnails.booking_service.repository.BookingRepository;
import com.ttnails.booking_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private void validateSlot(String service, LocalDateTime preferredDateTime, Long excludeBookingId) {
        LocalDateTime slotStart = preferredDateTime.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime slotEnd = slotStart.plusHours(1);

        long count = (excludeBookingId == null) ?
                bookingRepository.countByServiceAndPreferredDateTimeIsBetween(service, slotStart, slotEnd) :
                bookingRepository.countByServiceAndPreferredDateTimeIsBetweenAndIdNot(service, slotStart, slotEnd, excludeBookingId);

        if (count >= 10) throw new AppException(ErrorCode.SLOT_FULL);
    }

    private Booking buildBooking(Booking booking, String firstName, String lastName, String email, String phone,
                                 String service, LocalDateTime preferredDateTime, String note, User user) {
        booking.setFirstName(firstName);
        booking.setLastName(lastName);
        booking.setEmail(email);
        booking.setPhone(phone);
        booking.setService(service);
        booking.setPreferredDateTime(preferredDateTime);
        booking.setNote(note);
        booking.setUser(user);
        return booking;
    }

    private BookingDTO toDTO(Booking booking) {
        return new BookingDTO(booking);
    }

    public BookingDTO createBookingForGuest(GuestBookingRequest request) {
        validateSlot(request.getService(), request.getPreferredDateAndTime(), null);
        Booking booking = new Booking();
        bookingRepository.save(
                buildBooking(booking,
                        request.getFirstName(),
                        request.getLastName(),
                        request.getEmail(),
                        request.getPhone(),
                        request.getService(),
                        request.getPreferredDateAndTime(),
                        request.getNote(),
                        null
                )
        );
        return toDTO(booking);
    }

    public BookingDTO createBookingForUser(UserBookingRequest request, Jwt jwt) {
        String userId = jwt.getClaim("id");

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        validateSlot(request.getService(), request.getPreferredDateAndTime(), null);

        Booking booking = new Booking();
        bookingRepository.save(
                buildBooking(
                        booking,
                        user.getName(),
                        null,
                        user.getEmail(),
                        user.getPhone(),
                        request.getService(),
                        request.getPreferredDateAndTime(),
                        request.getNote(),
                        user
                )
        );
        return toDTO(booking);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public BookingDTO updateBookingForAdmin(Long id, GuestBookingRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        validateSlot(request.getService(), request.getPreferredDateAndTime(), id);
        bookingRepository.save(
                buildBooking(booking,
                        request.getFirstName(),
                        request.getLastName(),
                        request.getEmail(),
                        request.getPhone(),
                        request.getService(),
                        request.getPreferredDateAndTime(),
                        request.getNote(),
                        booking.getUser()
                )
        );
        return toDTO(booking);
    }

    public BookingDTO updateBookingForUser(Long id, UserBookingRequest request, Jwt jwt) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getUser() == null || !booking.getUser().getId().equals(UUID.fromString(jwt.getClaim("id"))))
            throw new AppException(ErrorCode.UNAUTHORIZED);

        validateSlot(request.getService(), request.getPreferredDateAndTime(), id);

        booking.setService(request.getService());
        booking.setPreferredDateTime(request.getPreferredDateAndTime());
        booking.setNote(request.getNote());

        bookingRepository.save(booking);
        return toDTO(booking);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingDTO> getAllBookings() {
        return bookingRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public BookingDTO getBookingById(Long id) {
        return bookingRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new NotFoundException("Can't find anything with id:" + id));
    }

    public List<BookingDTO> getBookingByUserId(UUID uuid) {
        return bookingRepository.findByUserId(uuid).stream().map(this::toDTO).collect(Collectors.toList());
    }
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteBooking(Long id) {
        bookingRepository.deleteById(id);
    }
}
