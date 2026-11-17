package com.ttnails.booking_service.staff.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Tạo/cập nhật thợ (S1-02). Khi PUT, field null = giữ nguyên;
 * {@code skills != null} = thay toàn bộ danh sách kỹ năng.
 */
@Data
public class StaffRequest {

    @NotBlank(message = "displayName is required")
    @Size(max = 255)
    private String displayName;

    /** Link tới tài khoản đăng nhập (optional) — để thợ xem lịch của mình. */
    private UUID userId;

    private Long branchId;

    @Size(max = 16)
    private String color;

    private Boolean active;

    @DecimalMin(value = "0.0", message = "commissionRate must be >= 0")
    @DecimalMax(value = "1.0", message = "commissionRate must be <= 1")
    private BigDecimal commissionRate;

    /** Danh sách kỹ năng — null khi update nghĩa là không đổi. */
    private List<SkillRequest> skills;
}
