package com.app.pharmacy.controller;

import com.app.pharmacy.config.PasswordEncoderConfig;
import com.app.pharmacy.config.SecurityConfig;
import com.app.pharmacy.domain.dtos.request.DrugControlledStatusUpdateRequest;
import com.app.pharmacy.domain.dtos.request.DrugCreateRequest;
import com.app.pharmacy.domain.dtos.request.DrugPriceUpdateRequest;
import com.app.pharmacy.domain.dtos.response.DrugResponse;
import com.app.pharmacy.domain.entity.enums.DosageForm;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.security.*;
import com.app.pharmacy.service.DrugService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rule 12 in practice: unit_price and is_controlled_substance are Admin-only.
 *
 * That restriction exists ONLY as @PreAuthorize on the controller — nothing in
 * DrugService or the Drug entity re-checks it — so a web-layer slice test is
 * the only place it can be verified at all.
 *
 * The real SecurityConfig, JWT filter, 401 entry point and 403 handler are
 * imported rather than mocked: a mocked entry point writes no status, which
 * would make an unauthenticated request look like a success.
 */
@WebMvcTest(DrugController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:8081",
        "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-for-hs256",
        "app.jwt.expiration-ms=3600000"
})
@DisplayName("DrugController — Rule 12 access control")
class DrugControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    /** Built locally: the Boot 4 WebMvc slice does not expose an ObjectMapper bean. */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @MockitoBean private DrugService drugService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final UUID DRUG_ID = UUID.randomUUID();

    private DrugResponse sampleDrug() {
        return new DrugResponse(DRUG_ID, "Paracetamol", "paracetamol",
                DosageForm.Tablet, "500mg", new BigDecimal("2.50"), false, 10);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Nested
    @DisplayName("Price updates — Admin only")
    class PriceUpdates {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin may change a price")
        void adminMayUpdatePrice() throws Exception {
            when(drugService.updatePrice(any(), any())).thenReturn(sampleDrug());

            mockMvc.perform(patch("/api/v1/drugs/{id}/price", DRUG_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DrugPriceUpdateRequest(new BigDecimal("9.99")))))
                    .andExpect(status().isOk())
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
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("Pharmacist is forbidden from changing a price")
        void pharmacistMayNotUpdatePrice() throws Exception {
            mockMvc.perform(patch("/api/v1/drugs/{id}/price", DRUG_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DrugPriceUpdateRequest(new BigDecimal("9.99")))))
                    .andExpect(status().isForbidden());

            verify(drugService, never()).updatePrice(any(), any());
        }

        @Test
        @Tag("known-defect")
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("Technician is forbidden from changing a price")
        void technicianMayNotUpdatePrice() throws Exception {
            mockMvc.perform(patch("/api/v1/drugs/{id}/price", DRUG_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DrugPriceUpdateRequest(new BigDecimal("9.99")))))
                    .andExpect(status().isForbidden());

            verify(drugService, never()).updatePrice(any(), any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller gets 401, not 403")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(patch("/api/v1/drugs/{id}/price", DRUG_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DrugPriceUpdateRequest(new BigDecimal("9.99")))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Controlled-substance status — Admin only")
    class ControlledStatus {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin may reclassify a drug as controlled")
        void adminMayReclassify() throws Exception {
            when(drugService.updateControlledStatus(any(), any())).thenReturn(sampleDrug());

            mockMvc.perform(patch("/api/v1/drugs/{id}/controlled-status", DRUG_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DrugControlledStatusUpdateRequest(true))))
                    .andExpect(status().isOk());
        }

        @Test
        @Tag("known-defect")
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("Pharmacist may not reclassify a drug")
        void pharmacistMayNotReclassify() throws Exception {
            mockMvc.perform(patch("/api/v1/drugs/{id}/controlled-status", DRUG_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DrugControlledStatusUpdateRequest(true))))
                    .andExpect(status().isForbidden());

            verify(drugService, never()).updateControlledStatus(any(), any());
        }
    }

    @Nested
    @DisplayName("Catalog entry and reads")
    class CatalogAccess {

        private DrugCreateRequest newDrug() {
            return new DrugCreateRequest("Paracetamol", "paracetamol",
                    DosageForm.Tablet, "500mg", new BigDecimal("2.50"), false, 10);
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("Technician may add a drug to the catalog")
        void technicianMayCreateDrug() throws Exception {
            when(drugService.createDrug(any())).thenReturn(sampleDrug());

            mockMvc.perform(post("/api/v1/drugs")
                            .contentType(MediaType.APPLICATION_JSON).content(json(newDrug())))
                    .andExpect(status().isCreated());
        }

        @Test
        @Tag("known-defect")
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin is NOT granted catalog entry — the annotation lists Pharmacist and Technician only")
        void adminMayNotCreateDrug() throws Exception {
            mockMvc.perform(post("/api/v1/drugs")
                            .contentType(MediaType.APPLICATION_JSON).content(json(newDrug())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("any authenticated role may read the catalog")
        void anyRoleMayReadCatalog() throws Exception {
            when(drugService.getAllDrugs()).thenReturn(List.of(sampleDrug()));

            mockMvc.perform(get("/api/v1/drugs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("Paracetamol"));
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller may not read the catalog")
        void anonymousMayNotReadCatalog() throws Exception {
            mockMvc.perform(get("/api/v1/drugs")).andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("routes a name query to the search path, not the full listing")
        void routesNameQueryToSearch() throws Exception {
            when(drugService.searchDrugsByName("para")).thenReturn(List.of(sampleDrug()));

            mockMvc.perform(get("/api/v1/drugs").param("name", "para")).andExpect(status().isOk());

            verify(drugService).searchDrugsByName("para");
            verify(drugService, never()).getAllDrugs();
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("treats a blank name query as no filter at all")
        void blankNameFallsBackToFullListing() throws Exception {
            when(drugService.getAllDrugs()).thenReturn(List.of(sampleDrug()));

            mockMvc.perform(get("/api/v1/drugs").param("name", "   ")).andExpect(status().isOk());

            verify(drugService).getAllDrugs();
            verify(drugService, never()).searchDrugsByName(any());
        }
    }

    @Nested
    @DisplayName("Error mapping")
    class ErrorMapping {

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a missing drug surfaces as 404, not 500")
        void notFoundMapsTo404() throws Exception {
            when(drugService.getDrugById(DRUG_ID))
                    .thenThrow(new ResourceNotFoundException("Drug not found: " + DRUG_ID));

            mockMvc.perform(get("/api/v1/drugs/{id}", DRUG_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Drug not found: " + DRUG_ID));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("a negative price is rejected as 400 with a field-level error")
        void validationFailureMapsTo400() throws Exception {
            mockMvc.perform(patch("/api/v1/drugs/{id}/price", DRUG_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DrugPriceUpdateRequest(new BigDecimal("-5.00")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.unitPrice").exists());

            verify(drugService, never()).updatePrice(any(), any());
        }

        /**
         * DEFECT-05. GlobalExceptionHandler has no handler for
         * MethodArgumentTypeMismatchException, so a client that sends a
         * non-UUID id gets 500 "An unexpected error occurred" and the server
         * logs a stack trace at ERROR for what is plainly a client mistake.
         * A malformed path variable is a 400.
         */
        @Test
        @Tag("known-defect")
        @WithMockUser(roles = "ADMIN")
        @DisplayName("a malformed UUID in the path is a 400, not a 500")
        void malformedPathVariableIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/drugs/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());

            verify(drugService, never()).getDrugById(any());
        }
    }
}
