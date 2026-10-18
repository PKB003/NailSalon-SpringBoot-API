package com.ttnails.booking_service.common.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {
    NOT_FOUND(404,"Can't find", HttpStatus.NOT_FOUND),
    SLOT_FULL(666,"This time slot is full. Please choose another time.", HttpStatus.NOT_ACCEPTABLE),
    USER_EXISTED(409, "User is already existed",HttpStatus.CONFLICT),
    EMAIL_IS_ALREADY_USED(409, "Email already exists", HttpStatus.CONFLICT),
    PHONE_IS_ALREADY_USED(409, "Phone already exists", HttpStatus.CONFLICT),
    ERROR_IN_GENERATING_TOKEN(1000, "Error generating token", HttpStatus.INTERNAL_SERVER_ERROR),
    FAILED_AUTHENTICATED(403,"Wrong log-in infos", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(405, "You don't have permission",HttpStatus.FORBIDDEN),
    INVALID_DATE(400, "Date must be in the future", HttpStatus.BAD_REQUEST),
    INVALID_TIME(400, "Invalid time format (expected HH:mm)", HttpStatus.BAD_REQUEST),
    INVALID_STATUS(400, "Invalid status value", HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(400, "Current password is incorrect", HttpStatus.BAD_REQUEST),
    CANCEL_TOO_LATE(400, "Stornierung nur bis 24 Stunden vor dem Termin möglich, bitte anrufen", HttpStatus.BAD_REQUEST),
    BOOKING_ALREADY_CANCELLED(409, "Termin wurde bereits storniert", HttpStatus.CONFLICT),
    BOOKING_ALREADY_COMPLETED(409, "Termin ist bereits abgeschlossen und kann nicht storniert werden", HttpStatus.CONFLICT),
    APPOINTMENT_PASSED(400, "Termin hat bereits stattgefunden und kann nicht mehr storniert werden", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION(409, "Statuswechsel ist nicht erlaubt", HttpStatus.CONFLICT),
    COMPLETION_TOO_EARLY(409, "Termin kann erst nach Beginn als abgeschlossen markiert werden", HttpStatus.CONFLICT),
    OUTSIDE_OPENING_HOURS(400, "Termin muss zwischen 09:00 und 18:00 liegen", HttpStatus.BAD_REQUEST),
    USER_HAS_BOOKINGS(409, "User hat noch Termine und kann nicht gelöscht werden", HttpStatus.CONFLICT),
    INVALID_LOGIN_REQUEST(400, "You must enter at least email or phone", HttpStatus.BAD_REQUEST)
    ;
    @Getter @Setter
    private int code;
    @Getter @Setter
    private String message;
    @Getter @Setter
    private HttpStatusCode httpStatusCode;

    ErrorCode(int code, String message, HttpStatusCode httpStatusCode){
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }

}
