package com.ttnails.booking_service.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class ApiResponse<T> {
    private int code;
    private String message;
    private LocalDateTime timeStamp;
    private T result;

    /**
     * Alias để frontend đọc được payload qua envelope "data"
     * (hàm unwrap phía frontend hỗ trợ cả raw JSON lẫn { "data": ... }).
     * Lưu ý: "result" vẫn được giữ nguyên để tương thích ngược (Postman/test cũ).
     */
    @JsonProperty("data")
    public T getData() {
        return result;
    }
}
