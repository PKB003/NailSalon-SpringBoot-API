package com.ttnails.booking_service.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ttnails.booking_service.common.web.RequestIdFilter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Envelope thống nhất cho mọi response (S0-02).
 *
 * <pre>
 * {
 *   "code": 200,                 // HTTP status
 *   "errorCode": "SLOT_FULL",    // null khi thành công, machine-readable
 *   "message": "Success",
 *   "details": [{"field": "...", "message": "..."}],
 *   "traceId": "...",            // khớp header X-Request-Id
 *   "timestamp": "2026-11-16T10:15:30",
 *   "data": {}                   // payload (alias cũ "result")
 * }
 * </pre>
 *
 * <p>{@code result} và {@code timeStamp} vẫn được serialize để không phá vỡ client cũ;
 * client mới dùng {@code data} và {@code timestamp}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String errorCode;
    private String message;
    private List<ValidationErrorDetail> details;
    private String traceId;
    private LocalDateTime timeStamp;
    private T result;

    /** Constructor tương thích ngược cho các controller hiện có. */
    public ApiResponse(int code, String message, LocalDateTime timeStamp, T result) {
        this.code = code;
        this.message = message;
        this.timeStamp = timeStamp;
        this.result = result;
        this.traceId = MDC.get(RequestIdFilter.MDC_KEY);
    }

    /**
     * Alias để frontend đọc được payload qua envelope "data".
     * "result" vẫn được giữ nguyên để tương thích ngược (Postman/test cũ).
     */
    @JsonProperty("data")
    public T getData() {
        return result;
    }

    /** Tên field chuẩn mới (S0-02); giữ "timeStamp" cho client cũ. */
    @JsonProperty("timestamp")
    public LocalDateTime getTimestamp() {
        return timeStamp;
    }

    public static <T> ApiResponse<T> success(int code, String message, T data) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timeStamp(LocalDateTime.now())
                .traceId(MDC.get(RequestIdFilter.MDC_KEY))
                .result(data)
                .build();
    }

    public static ApiResponse<Void> error(HttpStatus status, String errorCode, String message,
                                         List<ValidationErrorDetail> details) {
        return ApiResponse.<Void>builder()
                .code(status.value())
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .timeStamp(LocalDateTime.now())
                .traceId(MDC.get(RequestIdFilter.MDC_KEY))
                .build();
    }
}
