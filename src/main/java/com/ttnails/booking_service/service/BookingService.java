package com.ttnails.booking_service.service;


import com.ttnails.booking_service.dto.BookingRequest;
import com.ttnails.booking_service.entity.Booking;
import com.ttnails.booking_service.exception.NotFoundException;
import com.ttnails.booking_service.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    @Autowired
    private BookingRepository bookingRepository;

    public Booking createBooking(BookingRequest bookingRequest) {
        Booking booking = new Booking(bookingRequest.getFirstName(), bookingRequest.getLastName(), bookingRequest.getEmail(), bookingRequest.getPhone(), bookingRequest.getService(), bookingRequest.getPreferredDateAndTime(), bookingRequest.getNote());
        return bookingRepository.save(booking);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new NotFoundException("Can't find anything with id:" + id));
    }

    public  List<Booking> getBookingByUserId(UUID uuid) {
        return bookingRepository.findByUserId(uuid);
    }
    public Booking updateBooking(BookingRequest booking, Long id) {
        Booking book = bookingRepository.findById(id).orElse(null);
        if (book != null) {
            book.setFirstName(booking.getFirstName());
            book.setLastName(booking.getLastName());
            book.setEmail(booking.getEmail());
            book.setService(booking.getService());
            book.setPhone(booking.getPhone());
            book.setPreferredDateAndTime(booking.getPreferredDateAndTime());
            book.setNote(booking.getNote());
            return bookingRepository.save(book);
        } else {
            throw new NotFoundException("Can't find anything with id: " + id);
        }
    }

    public void deleteBooking(Long id) {
        bookingRepository.deleteById(id);
    }
}
