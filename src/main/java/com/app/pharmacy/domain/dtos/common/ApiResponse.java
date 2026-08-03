package com.app.pharmacy.domain.dtos.common;

import java.time.Instant;

/**
 * Uniform envelope for every API response — success and error alike.
 * Controllers wrap their DTOs with ApiResponse.success(data, message);
 * GlobalExceptionHandler and the security entry points/handlers all use
 * ApiResponse.error(message) so a client parses one shape everywhere,
 * regardless of which layer produced the response.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String timestamp
) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now().toString());
    }

    /** Convenience overload for endpoints that don't need a bespoke message. */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Request completed successfully");
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now().toString());
    }

    /** Same as error(message), but carries a data payload — e.g. field-level validation errors the client needs to render. */
    public static <T> ApiResponse<T> error(T data, String message) {
        return new ApiResponse<>(false, data, message, Instant.now().toString());
    }
}
