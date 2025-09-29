package com.ttnails.booking_service.controller;

import com.ttnails.booking_service.dto.ApiResponse;
import com.ttnails.booking_service.dto.BookingDTO;
import com.ttnails.booking_service.dto.GuestBookingRequest;
import com.ttnails.booking_service.dto.UserBookingRequest;
import com.ttnails.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public ApiResponse<List<BookingDTO>> getAllBookings(){
        return new ApiResponse<>(200,"Success",LocalDateTime.now(),bookingService.getAllBookings());
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingDTO> getBookingById(@PathVariable Long id){
        return new ApiResponse<>(200,"Success",LocalDateTime.now(),bookingService.getBookingById(id));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<BookingDTO>> getBookingByUser(@PathVariable UUID userId){
        return new ApiResponse<>(200,"Success",LocalDateTime.now(),bookingService.getBookingByUserId(userId));
    }

    @PostMapping("/guest")
    public ApiResponse<BookingDTO> createGuestBooking(@RequestBody @Valid GuestBookingRequest request) {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), bookingService.createBookingForGuest(request));
    }

    @PostMapping("/user")
    public ApiResponse<BookingDTO> createUserBooking(
            @RequestBody @Valid UserBookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        BookingDTO booking = bookingService.createBookingForUser(request, jwt);
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), booking);
    }

    @PutMapping("/guest/{id}")
    public ApiResponse<BookingDTO> updateBookingByAdmin(@PathVariable Long id,
                                                        @RequestBody @Valid GuestBookingRequest request) {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(),
                bookingService.updateBookingForAdmin(id, request));
    }

    @PutMapping("/user/{id}")
    public ApiResponse<BookingDTO> updateBookingByUser(@PathVariable Long id,
                                                       @RequestBody @Valid UserBookingRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(),
                bookingService.updateBookingForUser(id, request, jwt));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBooking(@PathVariable Long id){
        bookingService.deleteBooking(id);
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), null);
    }
}
