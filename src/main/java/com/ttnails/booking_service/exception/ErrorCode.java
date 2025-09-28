package com.ttnails.booking_service.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {
    NOT_FOUND(404,"Can't find", HttpStatus.NOT_FOUND),
    USER_EXISTED(409, "User is already existed",HttpStatus.CONFLICT),
    EMAIL_IS_ALREADY_USED(409, "Email already exists", HttpStatus.CONFLICT),
    PHONE_IS_ALREADY_USED(409, "Phone already exists", HttpStatus.CONFLICT),
    ERROR_IN_GENERATING_TOKEN(1000, "Error generating token", HttpStatus.INTERNAL_SERVER_ERROR),
    FAILED_AUTHENTICATED(403,"Wrong log-in infos", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(405, "You don't have permission",HttpStatus.FORBIDDEN)
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
