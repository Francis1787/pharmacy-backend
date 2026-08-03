package com.app.pharmacy.exception;

/** Thrown on a uniqueness conflict (email, phone, license number, ...). Mapped to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
