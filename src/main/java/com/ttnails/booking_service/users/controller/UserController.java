package com.ttnails.booking_service.users.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.users.dto.UserDTO;
import com.ttnails.booking_service.users.dto.UserRequest;
import com.ttnails.booking_service.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Quản trị user (S0-03): trả DTO thay vì entity, có phân trang.
 * Không còn expose password/roles/bookingList ra ngoài.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Quản trị người dùng (ADMIN)")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách user (phân trang, chỉ ADMIN)")
    public ApiResponse<PageResponse<UserDTO>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return ApiResponse.success(200, "Success",
                userService.getAllUsers(page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết user")
    public ApiResponse<UserDTO> getUserById(@PathVariable UUID id) {
        return ApiResponse.success(200, "Success", UserDTO.from(userService.getUserById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật user")
    public ApiResponse<UserDTO> updateUser(@RequestBody @Valid UserRequest userRequest, @PathVariable UUID id) {
        return ApiResponse.success(200, "Success",
                UserDTO.from(userService.updateUser(id, userRequest)));
    }

    @PostMapping
    @Operation(summary = "Tạo user")
    public ApiResponse<UserDTO> createUser(@RequestBody @Valid UserRequest userRequest) {
        return ApiResponse.success(200, "Success",
                UserDTO.from(userService.createUser(userRequest)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa user (chặn nếu còn booking)")
    public ApiResponse<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ApiResponse.success(200, "Success", null);
    }
}
