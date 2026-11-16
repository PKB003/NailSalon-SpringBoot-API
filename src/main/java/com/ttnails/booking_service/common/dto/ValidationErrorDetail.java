package com.ttnails.booking_service.common.dto;

/**
 * Một lỗi validation ở cấp field, dùng trong {@link ApiResponse#getDetails()}.
 */
public record ValidationErrorDetail(String field, String message) {
}
