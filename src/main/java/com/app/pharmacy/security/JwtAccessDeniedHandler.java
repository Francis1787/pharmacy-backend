package com.app.pharmacy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Fires when an authenticated staff member's role fails a @PreAuthorize
 * check (e.g. a Technician hitting an approve-prescription endpoint).
 * Returns 403 JSON rather than the default HTML error page.
 *
 * Builds its own ObjectMapper rather than injecting Spring's
 * auto-configured one — this handler only ever serializes a plain Map,
 * so it doesn't need any app-wide Jackson customization (date modules,
 * naming strategy, etc.), and staying self-contained avoids depending
 * on that auto-configured bean being present at all.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 403,
                "error", "Forbidden",
                "message", "Your role does not have permission to perform this action",
                "path", request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
