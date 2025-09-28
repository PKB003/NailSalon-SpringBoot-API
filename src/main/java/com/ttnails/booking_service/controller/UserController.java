package com.ttnails.booking_service.controller;

import com.ttnails.booking_service.dto.ApiResponse;
import com.ttnails.booking_service.dto.UserRequest;
import com.ttnails.booking_service.entity.User;
import com.ttnails.booking_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;
    @GetMapping
    public ApiResponse<List<User>> getAllUsers() {
        return new ApiResponse<>(200,"Success", LocalDateTime.now(), userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable UUID id) {
        return new ApiResponse<>(200,"Success", LocalDateTime.now(), userService.getUserById(id));
    }
    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(@RequestBody @Valid UserRequest userRequest, @PathVariable UUID id) {
        User user = userService.updateUser(id,userRequest);
        return new ApiResponse<>(200,"Success", LocalDateTime.now(), user);
    }

    @PostMapping
    public ApiResponse<User> createUser(@RequestBody @Valid UserRequest userRequest) {
        return new ApiResponse<>(200,"Success", LocalDateTime.now(), userService.createUser(userRequest));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<User> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return new ApiResponse<>(200,"Success", LocalDateTime.now(), null);
    }
}
