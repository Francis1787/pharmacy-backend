package com.app.pharmacy.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the "Authorize" button in Swagger UI (springdoc.swagger-ui.path,
 * configured in application.properties). Without the @SecurityScheme +
 * @SecurityRequirement pair below, springdoc still lists every endpoint,
 * but there's no way to attach a Bearer token to a "Try it out" request —
 * every protected route would show 401 regardless of whether you're
 * actually logged in, since nothing in Swagger UI would be sending the
 * Authorization header.
 *
 * @SecurityRequirement at this class level applies "bearerAuth" globally
 * to every operation by default; AuthController.login overrides this
 * locally (see its @SecurityRequirements(...) with an empty array) since
 * it's the one endpoint that's actually public.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Adom Community Pharmacy API",
                version = "v1",
                description = "REST API for Adom Community Pharmacy — a single-location, "
                        + "walk-in-only, prescription-drug-only pharmacy management system. "
                        + "Covers prescription intake/approval, dispensing, controlled-substance "
                        + "tracking, inventory/batch management, procurement, and staff/audit "
                        + "compliance across the Pharmacist, Technician, and Admin roles.",
                contact = @Contact(name = "Adom Community Pharmacy", email = "francis.danso@adompharmacy.com")
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the token returned from POST /api/v1/auth/login (without the word 'Bearer')."
)
public class OpenApiConfig {
}
