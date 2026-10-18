package com.ttnails.booking_service.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttnails.booking_service.common.enums.Role;
import com.ttnails.booking_service.users.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User shape mà frontend mong đợi:
 * { id, name, email, phone, role: "client" | "admin", birthday }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String birthday;

    public static UserDTO from(User user) {
        return UserDTO.builder()
                .id(user.getId() != null ? user.getId().toString() : null)
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRoles() != null && user.getRoles().contains(Role.ADMIN.name()) ? "admin" : "client")
                .birthday(user.getDob() != null ? user.getDob().toString() : null)
                .build();
    }
}
