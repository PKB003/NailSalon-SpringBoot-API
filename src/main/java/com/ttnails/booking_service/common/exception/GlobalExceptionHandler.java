package com.ttnails.booking_service.common.exception;

import com.ttnails.booking_service.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<?>> notFoundException(NotFoundException ex){
        ApiResponse<?> response = new ApiResponse<>();
        response.setCode(404);
        response.setMessage(ex.getMessage());
        response.setTimeStamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body( response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> AccessDeniedException(AccessDeniedException e) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        ApiResponse<?> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        response.setTimeStamp(LocalDateTime.now());
        return ResponseEntity.status(errorCode.getHttpStatusCode()).body(response);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<?>> AppException(AppException e){
        ApiResponse<?> response = new ApiResponse<>();
        response.setCode(e.getErrorCode().getCode());
        response.setMessage(e.getMessage());
        response.setTimeStamp(LocalDateTime.now());
        return ResponseEntity.status(e.getErrorCode().getHttpStatusCode()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> invalidArgs(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();

        ApiResponse<?> response = new ApiResponse<>();
        response.setCode(400);
        response.setMessage("Validation failed: " + errors);
        response.setTimeStamp(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** Lỗi ràng buộc DB (vd: xóa user đang có booking) → 409 thay vì 500 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> dataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        ApiResponse<?> response = new ApiResponse<>();
        response.setCode(409);
        response.setMessage("Datenkonflikt: die Anfrage verletzt bestehende Verknüpfungen");
        response.setTimeStamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /** Body JSON sai định dạng → 400 thay vì 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> unreadableBody(HttpMessageNotReadableException e) {
        ApiResponse<?> response = new ApiResponse<>();
        response.setCode(400);
        response.setMessage("Malformed request body");
        response.setTimeStamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** Fallback mọi lỗi không lường trước → 500 với message chung, không lộ internal */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> unexpected(Exception e) {
        log.error("Unexpected error", e);
        ApiResponse<?> response = new ApiResponse<>();
        response.setCode(500);
        response.setMessage("Internal server error");
        response.setTimeStamp(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
