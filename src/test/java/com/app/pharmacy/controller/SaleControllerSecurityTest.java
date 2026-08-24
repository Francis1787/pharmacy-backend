package com.app.pharmacy.controller;

import com.app.pharmacy.config.PasswordEncoderConfig;
import com.app.pharmacy.config.SecurityConfig;
import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.request.SaleCreateRequest;
import com.app.pharmacy.domain.dtos.response.SaleResponse;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.security.*;
import com.app.pharmacy.service.SaleService;
import com.app.pharmacy.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rule 15 at the HTTP boundary, plus the two things a dispensing client must
 * be able to rely on: the caller's own identity is taken from the token
 * (never from the body), and a refused sale comes back as a distinguishable
 * 422 rather than a generic error.
 */
@WebMvcTest(SaleController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:8081",
        "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-for-hs256",
        "app.jwt.expiration-ms=3600000"
})
@DisplayName("SaleController — Rule 15 access control")
class SaleControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    /** Built locally: the Boot 4 WebMvc slice does not expose an ObjectMapper bean. */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @MockitoBean private SaleService saleService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private Staff pharmacist;
    private SaleCreateRequest request;

    @BeforeEach
    void setUp() {
        pharmacist = TestFixtures.pharmacist();
        request = new SaleCreateRequest(UUID.randomUUID(), PaymentMethod.Cash, null, null,
                List.of(new SaleCreateRequest.Item(UUID.randomUUID(), 2)));
    }

    /** Authenticates as a real CustomUserDetails so @AuthenticationPrincipal resolves. */
    private RequestPostProcessor asPharmacist() {
        return user(new CustomUserDetails(pharmacist));
    }

    private SaleResponse sampleSale() {
        return new SaleResponse(UUID.randomUUID(),
                new RefSummary(UUID.randomUUID(), "prescription"),
                new RefSummary(pharmacist.getId(), pharmacist.getFullName()),
                new RefSummary(pharmacist.getId(), pharmacist.getFullName()),
                LocalDateTime.now(), new BigDecimal("25.00"), PaymentMethod.Cash, List.of());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Nested
    @DisplayName("Completing a sale")
    class Dispensing {

        @Test
        @DisplayName("a Pharmacist may complete a sale and gets 201")
        void pharmacistMayDispense() throws Exception {
            when(saleService.createSale(any(), any())).thenReturn(sampleSale());

            mockMvc.perform(post("/api/v1/sales").with(asPharmacist())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        /**
         * DEFECT-06. Access IS correctly denied, but the response is wrong:
         * @PreAuthorize raises AuthorizationDeniedException inside the handler
         * invocation, where GlobalExceptionHandler's catch-all
         * @ExceptionHandler(Exception.class) claims it before Spring Security's
         * ExceptionTranslationFilter can. The caller gets 500 "An unexpected
         * error occurred", the server logs a stack trace at ERROR for a routine
         * permission check, and JwtAccessDeniedHandler — the class written to
         * produce this exact 403 body — is unreachable dead code.
         *
         * Affects every role rule in the system (Rules 2, 11, 12, 15, 16).
         */
        @Test
        @Tag("known-defect")
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician is forbidden from completing a sale")
        void technicianMayNotDispense() throws Exception {
            mockMvc.perform(post("/api/v1/sales")
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isForbidden());

            verify(saleService, never()).createSale(any(), any());
        }

        @Test
        @Tag("known-defect")
        @WithMockUser(roles = "ADMIN")
        @DisplayName("an Admin is forbidden from completing a sale")
        void adminMayNotDispense() throws Exception {
            mockMvc.perform(post("/api/v1/sales")
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isForbidden());

            verify(saleService, never()).createSale(any(), any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller gets 401")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/sales")
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("takes the acting staff id from the token, never from the request body")
        void staffIdComesFromToken() throws Exception {
            when(saleService.createSale(any(), any())).thenReturn(sampleSale());

            mockMvc.perform(post("/api/v1/sales").with(asPharmacist())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isCreated());

            verify(saleService).createSale(any(SaleCreateRequest.class), eq(pharmacist.getId()));
        }

        @Test
        @DisplayName("a refused sale surfaces as 422 with the rule that blocked it")
        void businessRuleViolationMapsTo422() throws Exception {
            when(saleService.createSale(any(), any())).thenThrow(new BusinessRuleViolationException(
                    "Batch BN-001 expired on 2026-01-01 and cannot be sold (Rule 5)"));

            mockMvc.perform(post("/api/v1/sales").with(asPharmacist())
                            .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Rule 5")));
        }

        @Test
        @DisplayName("an empty item list is rejected as 400 before reaching the service")
        void emptyItemsRejected() throws Exception {
            SaleCreateRequest empty = new SaleCreateRequest(
                    UUID.randomUUID(), PaymentMethod.Cash, null, null, List.of());

            mockMvc.perform(post("/api/v1/sales").with(asPharmacist())
                            .contentType(MediaType.APPLICATION_JSON).content(json(empty)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.items").exists());

            verify(saleService, never()).createSale(any(), any());
        }

        /**
         * DEFECT-05 (second instance). An unparseable enum in the request body
         * raises HttpMessageNotReadableException, which GlobalExceptionHandler
         * does not handle, so it falls through to the catch-all and the client
         * gets 500 "An unexpected error occurred" for a malformed request.
         */
        @Test
        @Tag("known-defect")
        @DisplayName("an unknown payment method is a client error, not a 500")
        void unknownPaymentMethodRejected() throws Exception {
            String body = """
                    {"prescriptionId":"%s","paymentMethod":"Bitcoin","items":[{"batchId":"%s","quantitySold":1}]}
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/api/v1/sales").with(asPharmacist())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is4xxClientError());

            verify(saleService, never()).createSale(any(), any());
        }
    }

    @Nested
    @DisplayName("Reading sales / receipts")
    class Reads {

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("routes a pharmacistId filter to the by-pharmacist query")
        void routesPharmacistFilter() throws Exception {
            UUID pharmacistId = UUID.randomUUID();
            when(saleService.getSalesByDispensingPharmacist(pharmacistId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/sales").param("pharmacistId", pharmacistId.toString()))
                    .andExpect(status().isOk());

            verify(saleService).getSalesByDispensingPharmacist(pharmacistId);
            verify(saleService, never()).getAllSales();
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("routes a from/to pair to the date-range query")
        void routesDateRange() throws Exception {
            when(saleService.getSalesByDateRange(any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/sales")
                            .param("from", "2026-01-01T00:00:00")
                            .param("to", "2026-01-31T23:59:59"))
                    .andExpect(status().isOk());

            verify(saleService).getSalesByDateRange(any(), any());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("falls back to the full listing when only one end of the range is given")
        void partialDateRangeFallsBackToAll() throws Exception {
            when(saleService.getAllSales()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/sales").param("from", "2026-01-01T00:00:00"))
                    .andExpect(status().isOk());

            verify(saleService).getAllSales();
            verify(saleService, never()).getSalesByDateRange(any(), any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller cannot read a receipt")
        void anonymousCannotReadReceipt() throws Exception {
            mockMvc.perform(get("/api/v1/sales/{id}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
