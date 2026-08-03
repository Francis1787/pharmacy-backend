package com.app.pharmacy.exception;

/** Thrown when a lookup by ID (or similar) finds nothing. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
