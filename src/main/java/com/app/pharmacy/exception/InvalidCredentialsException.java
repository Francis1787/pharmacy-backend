package com.app.pharmacy.exception;

/** Thrown on a failed login attempt (unknown email or wrong password). Mapped to HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
