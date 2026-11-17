package com.ttnails.booking_service.staff.entity;

import com.ttnails.booking_service.users.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Nhân viên (D3): entity riêng, có thể link tới {@link User} để đăng nhập.
 *
 * <p>Role {@code NAIL_TECH} nhìn thấy lịch của chính mình qua {@code staff.user_id}.
 * Nhân viên không cần tài khoản (ví dụ thợ part-time) — {@code userId} để trống.</p>
 */
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Màu hiển thị trên lịch (hex), ví dụ #E91E63. */
    @Column(length = 16)
    private String color;

    @Column(nullable = false)
    private boolean active;

    /** Tỷ lệ hưởng trên doanh thu dịch vụ (0..1), dùng cho báo cáo commission ở S5. */
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    public boolean hasUser() {
        return user != null;
    }
}
