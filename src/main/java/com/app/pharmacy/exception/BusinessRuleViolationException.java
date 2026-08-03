package com.app.pharmacy.exception;

/**
 * Thrown when a request is well-formed but violates a pharmacy business rule
 * (e.g. dispensing against an unapproved prescription, selling an expired
 * batch, editing a restricted field). Mapped to HTTP 422/400.
 */
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
