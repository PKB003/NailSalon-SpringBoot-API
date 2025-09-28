package com.ttnails.booking_service.controller;

import com.ttnails.booking_service.dto.ApiResponse;
import com.ttnails.booking_service.dto.BookingRequest;
import com.ttnails.booking_service.entity.Booking;
import com.ttnails.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/booking")
public class BookingController {
    @Autowired
    private BookingService bookingService;

    @GetMapping
    public ApiResponse<List<Booking>> getAllBookings(){
        return new ApiResponse<>(200,"Success",LocalDateTime.now(),bookingService.getAllBookings());
    }
    @GetMapping("/{id}")
    public ApiResponse<Booking> getBookingById(@PathVariable Long id){
        return new ApiResponse<>(200,"Success",LocalDateTime.now(),bookingService.getBookingById(id));
    }
    @GetMapping("/user/{userId}")
    public ApiResponse<List<Booking>> getBookingByUser(@PathVariable UUID userId){
        return new ApiResponse<>(200,"Success",LocalDateTime.now(),bookingService.getBookingByUserId(userId));
    }
    @PostMapping
    public ApiResponse<Booking> createBooking(@RequestBody @Valid BookingRequest booking){
        return new ApiResponse<>(200,"Success", LocalDateTime.now(),bookingService.createBooking(booking));
    }
    @PutMapping("/{id}")
    public ApiResponse<Booking> updateBooking(@PathVariable Long id, @RequestBody @Valid BookingRequest booking) {
        return new ApiResponse<>(200,"Success",LocalDateTime.now(),bookingService.updateBooking(booking,id));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Booking> deleteBooking(@PathVariable Long id){
        bookingService.deleteBooking(id);
        return new ApiResponse<>(200, "Success", LocalDateTime.now(),null);
    }

}
