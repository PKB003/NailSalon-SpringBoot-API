package com.ttnails.booking_service.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi nghiệp vụ. {@code name()} là {@code errorCode} trả về cho client,
 * {@code httpStatus} là HTTP status tương ứng (S0-02).
 */
@Getter
public enum ErrorCode {
    // 400
    INVALID_DATE("Date must be in the future", HttpStatus.BAD_REQUEST),
    INVALID_TIME("Invalid time format (expected HH:mm)", HttpStatus.BAD_REQUEST),
    INVALID_STATUS("Invalid status value", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("Invalid request", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("Malformed request body", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("Validation failed", HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD("Current password is incorrect", HttpStatus.BAD_REQUEST),
    CANCEL_TOO_LATE("Stornierung nur bis 24 Stunden vor dem Termin möglich, bitte anrufen", HttpStatus.BAD_REQUEST),
    APPOINTMENT_PASSED("Termin hat bereits stattgefunden und kann nicht mehr storniert werden", HttpStatus.BAD_REQUEST),
    OUTSIDE_OPENING_HOURS("Termin muss zwischen 09:00 und 18:00 liegen", HttpStatus.BAD_REQUEST),
    INVALID_LOGIN_REQUEST("You must enter at least email or phone", HttpStatus.BAD_REQUEST),

    // 401 / 403
    FAILED_AUTHENTICATED("Wrong log-in infos", HttpStatus.UNAUTHORIZED),
    AUTHENTICATION_REQUIRED("Authentication required", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("You don't have permission", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("You don't have permission", HttpStatus.FORBIDDEN),

    // 404 / 405 / 406 / 409 / 415
    NOT_FOUND("Can't find", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("HTTP method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    SLOT_FULL("This time slot is full. Please choose another time.", HttpStatus.CONFLICT),
    USER_EXISTED("User is already existed", HttpStatus.CONFLICT),
    EMAIL_IS_ALREADY_USED("Email already exists", HttpStatus.CONFLICT),
    PHONE_IS_ALREADY_USED("Phone already exists", HttpStatus.CONFLICT),
    BOOKING_ALREADY_CANCELLED("Termin wurde bereits storniert", HttpStatus.CONFLICT),
    BOOKING_ALREADY_COMPLETED("Termin ist bereits abgeschlossen und kann nicht storniert werden", HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION("Statuswechsel ist nicht erlaubt", HttpStatus.CONFLICT),
    COMPLETION_TOO_EARLY("Termin kann erst nach Beginn als abgeschlossen markiert werden", HttpStatus.CONFLICT),
    USER_HAS_BOOKINGS("User hat noch Termine und kann nicht gelöscht werden", HttpStatus.CONFLICT),
    DATA_INTEGRITY_VIOLATION("Datenkonflikt: die Anfrage verletzt bestehende Verknüpfungen", HttpStatus.CONFLICT),

    // 500
    ERROR_IN_GENERATING_TOKEN("Error generating token", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
