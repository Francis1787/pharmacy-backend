package com.app.pharmacy.controller;

import com.app.pharmacy.config.PasswordEncoderConfig;
import com.app.pharmacy.config.SecurityConfig;
import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.request.*;
import com.app.pharmacy.domain.dtos.response.*;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.security.*;
import com.app.pharmacy.service.BatchService;
import com.app.pharmacy.service.CustomerService;
import com.app.pharmacy.service.DoctorService;
import com.app.pharmacy.service.PrescriptionService;
import com.app.pharmacy.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The four patient-facing surfaces: prescriptions, stock batches, customers
 * and prescribers (Rules 2, 5, 11).
 *
 * <p>As in {@code AdminControllersSecurityTest}, a denied request is asserted
 * by verifying the service was never invoked rather than by asserting 403 —
 * see DEFECT-06 and the note on that class. The security decision is what is
 * checked here; the status-code contract is pinned once elsewhere.
 */
@WebMvcTest({PrescriptionController.class, BatchController.class,
        CustomerController.class, DoctorController.class})
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:8081",
        "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-for-hs256",
        "app.jwt.expiration-ms=3600000"
})
@DisplayName("Clinical controllers — access control")
class ClinicalControllersSecurityTest {

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @MockitoBean private PrescriptionService prescriptionService;
    @MockitoBean private BatchService batchService;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private DoctorService doctorService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private Staff pharmacist;

    @BeforeEach
    void setUp() {
        pharmacist = TestFixtures.pharmacist();
    }

    private RequestPostProcessor asPharmacist() {
        return user(new CustomUserDetails(pharmacist));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ───────────────────────── Prescriptions ─────────────────────────

    @Nested
    @DisplayName("PrescriptionController — Rule 2, only a Pharmacist verifies")
    class Prescriptions {

        private PrescriptionResponse response(ApprovalStatus status) {
            return new PrescriptionResponse(UUID.randomUUID(),
                    new RefSummary(UUID.randomUUID(), "Efua Sarpong"),
                    new RefSummary(UUID.randomUUID(), "Dr. Kwame Asante"),
                    LocalDate.now().minusDays(1), LocalDateTime.now(),
                    status == ApprovalStatus.Pending ? null : new RefSummary(pharmacist.getId(), pharmacist.getFullName()),
                    status, null, List.of());
        }

        private PrescriptionCreateRequest createRequest() {
            return new PrescriptionCreateRequest(UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.now().minusDays(1), "Take after meals",
                    List.of(new PrescriptionCreateRequest.Item(UUID.randomUUID(), "1 tablet twice daily", 20)));
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician may log an incoming prescription")
        void technicianMayLogIntake() throws Exception {
            when(prescriptionService.createPrescription(any())).thenReturn(response(ApprovalStatus.Pending));

            mockMvc.perform(post("/api/v1/prescriptions")
                            .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.approvalStatus").value("Pending"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("an Admin may not log a prescription — clinical intake is not an admin function")
        void adminCannotLogIntake() throws Exception {
            mockMvc.perform(post("/api/v1/prescriptions")
                    .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())));

            verify(prescriptionService, never()).createPrescription(any());
        }

        @Test
        @DisplayName("a Pharmacist may approve, attributed to the token's staff id")
        void pharmacistMayApprove() throws Exception {
            UUID id = UUID.randomUUID();
            when(prescriptionService.approvePrescription(any(), any())).thenReturn(response(ApprovalStatus.Approved));

            mockMvc.perform(patch("/api/v1/prescriptions/{id}/approve", id).with(asPharmacist()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.approvalStatus").value("Approved"));

            verify(prescriptionService).approvePrescription(eq(id), eq(pharmacist.getId()));
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician may not approve a prescription (Rule 2)")
        void technicianCannotApprove() throws Exception {
            mockMvc.perform(patch("/api/v1/prescriptions/{id}/approve", UUID.randomUUID()));

            verify(prescriptionService, never()).approvePrescription(any(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("an Admin may not approve a prescription (Rule 2)")
        void adminCannotApprove() throws Exception {
            mockMvc.perform(patch("/api/v1/prescriptions/{id}/approve", UUID.randomUUID()));

            verify(prescriptionService, never()).approvePrescription(any(), any());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician may not reject a prescription either")
        void technicianCannotReject() throws Exception {
            mockMvc.perform(patch("/api/v1/prescriptions/{id}/reject", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new PrescriptionRejectRequest("Illegible"))));

            verify(prescriptionService, never()).rejectPrescription(any(), any(), any());
        }

        @Test
        @DisplayName("re-approving an already-approved prescription surfaces as 422")
        void doubleApprovalIs422() throws Exception {
            when(prescriptionService.approvePrescription(any(), any()))
                    .thenThrow(new BusinessRuleViolationException("Prescription is Approved and cannot be approved again"));

            mockMvc.perform(patch("/api/v1/prescriptions/{id}/approve", UUID.randomUUID()).with(asPharmacist()))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a future-dated prescription is rejected as 400")
        void futureDatedPrescriptionRejected() throws Exception {
            PrescriptionCreateRequest invalid = new PrescriptionCreateRequest(
                    UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().plusDays(1), null,
                    List.of(new PrescriptionCreateRequest.Item(UUID.randomUUID(), "1 daily", 10)));

            mockMvc.perform(post("/api/v1/prescriptions")
                            .contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.dateIssued").exists());

            verify(prescriptionService, never()).createPrescription(any());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("routes a status filter to the by-status query")
        void routesStatusFilter() throws Exception {
            when(prescriptionService.getPrescriptionsByStatus(ApprovalStatus.Pending)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/prescriptions").param("status", "Pending"))
                    .andExpect(status().isOk());

            verify(prescriptionService).getPrescriptionsByStatus(ApprovalStatus.Pending);
            verify(prescriptionService, never()).getAllPrescriptions();
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller gets 401")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/prescriptions")).andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────── Batches ───────────────────────────

    @Nested
    @DisplayName("BatchController — Rule 11, only a Pharmacist verifies a delivery")
    class Batches {

        private BatchResponse batchResponse(boolean verified) {
            return new BatchResponse(UUID.randomUUID(),
                    new RefSummary(UUID.randomUUID(), "Paracetamol"), "BN-2026-001", 250,
                    LocalDate.now().plusYears(1), false, LocalDate.now(),
                    new RefSummary(UUID.randomUUID(), "Accra Medical Supplies"), null,
                    verified ? new RefSummary(pharmacist.getId(), pharmacist.getFullName()) : null,
                    false);
        }

        private BatchCreateRequest createRequest() {
            return new BatchCreateRequest(UUID.randomUUID(), "BN-2026-001", 250,
                    LocalDate.now().plusYears(1), UUID.randomUUID(), null);
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician may receive stock, and it arrives unverified")
        void technicianMayReceiveStock() throws Exception {
            when(batchService.createBatch(any())).thenReturn(batchResponse(false));

            mockMvc.perform(post("/api/v1/batches")
                            .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.verifiedByPharmacist").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("an Admin may not receive stock")
        void adminCannotReceiveStock() throws Exception {
            mockMvc.perform(post("/api/v1/batches")
                    .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())));

            verify(batchService, never()).createBatch(any());
        }

        @Test
        @DisplayName("a Pharmacist may verify a delivery, attributed to the token's staff id")
        void pharmacistMayVerify() throws Exception {
            UUID batchId = UUID.randomUUID();
            when(batchService.verifyBatch(any(), any())).thenReturn(batchResponse(true));

            mockMvc.perform(patch("/api/v1/batches/{id}/verify", batchId).with(asPharmacist()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.verifiedByPharmacist.id").value(pharmacist.getId().toString()));

            verify(batchService).verifyBatch(eq(batchId), eq(pharmacist.getId()));
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician may not verify a delivery (Rule 11)")
        void technicianCannotVerify() throws Exception {
            mockMvc.perform(patch("/api/v1/batches/{id}/verify", UUID.randomUUID()));

            verify(batchService, never()).verifyBatch(any(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("an Admin may not verify a delivery (Rule 11)")
        void adminCannotVerify() throws Exception {
            mockMvc.perform(patch("/api/v1/batches/{id}/verify", UUID.randomUUID()));

            verify(batchService, never()).verifyBatch(any(), any());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("stock already expired at intake is rejected as 400 (Rule 5)")
        void expiredStockRejectedAtIntake() throws Exception {
            BatchCreateRequest expired = new BatchCreateRequest(UUID.randomUUID(), "BN-OLD", 10,
                    LocalDate.now().minusDays(1), UUID.randomUUID(), null);

            mockMvc.perform(post("/api/v1/batches")
                            .contentType(MediaType.APPLICATION_JSON).content(json(expired)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.expiryDate").exists());

            verify(batchService, never()).createBatch(any());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("routes the expiring-within-days filter to the expiry report (Rule 6)")
        void routesExpiringFilter() throws Exception {
            when(batchService.getBatchesExpiringWithinDays(30)).thenReturn(List.of(batchResponse(true)));

            mockMvc.perform(get("/api/v1/batches").param("expiring-within-days", "30"))
                    .andExpect(status().isOk());

            verify(batchService).getBatchesExpiringWithinDays(30);
            verify(batchService, never()).getAllBatches();
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("routes a drugId filter to the by-drug query")
        void routesDrugFilter() throws Exception {
            UUID drugId = UUID.randomUUID();
            when(batchService.getBatchesByDrug(drugId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/batches").param("drugId", drugId.toString()))
                    .andExpect(status().isOk());

            verify(batchService).getBatchesByDrug(drugId);
        }
    }

    // ──────────────────── Customers and doctors ────────────────────

    @Nested
    @DisplayName("CustomerController and DoctorController — Pharmacist writes")
    class Directory {

        private CustomerResponse customer() {
            return new CustomerResponse(UUID.randomUUID(), "Efua Sarpong", "+233240000001",
                    "5 Oxford Street, Osu", LocalDateTime.now());
        }

        private DoctorResponse doctor() {
            return new DoctorResponse(UUID.randomUUID(), "Dr. Kwame Asante", "MD-4471", "+233270000001");
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist may register a customer")
        void pharmacistMayCreateCustomer() throws Exception {
            when(customerService.createCustomer(any())).thenReturn(customer());

            mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CustomerRequest("Efua Sarpong", "+233240000001", "5 Oxford Street"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician may read customers but not create one")
        void technicianReadsButCannotWriteCustomer() throws Exception {
            when(customerService.getAllCustomers()).thenReturn(List.of(customer()));

            mockMvc.perform(get("/api/v1/customers")).andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                    .content(json(new CustomerRequest("Efua Sarpong", "+233240000001", null))));

            verify(customerService).getAllCustomers();
            verify(customerService, never()).createCustomer(any());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a malformed customer phone number is rejected as 400")
        void malformedPhoneRejected() throws Exception {
            mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CustomerRequest("Efua Sarpong", "12345", null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());

            verify(customerService, never()).createCustomer(any());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("routes a customer name query to the search path")
        void routesCustomerSearch() throws Exception {
            when(customerService.searchCustomersByName("efua")).thenReturn(List.of(customer()));

            mockMvc.perform(get("/api/v1/customers").param("name", "efua")).andExpect(status().isOk());

            verify(customerService).searchCustomersByName("efua");
            verify(customerService, never()).getAllCustomers();
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist may register a prescriber")
        void pharmacistMayCreateDoctor() throws Exception {
            when(doctorService.createDoctor(any())).thenReturn(doctor());

            mockMvc.perform(post("/api/v1/doctors").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DoctorRequest("Dr. Kwame Asante", "MD-4471", "+233270000001"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician may not register a prescriber")
        void technicianCannotCreateDoctor() throws Exception {
            mockMvc.perform(post("/api/v1/doctors").contentType(MediaType.APPLICATION_JSON)
                    .content(json(new DoctorRequest("Dr. Impostor", "MD-0000", null))));

            verify(doctorService, never()).createDoctor(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("an Admin may not update a prescriber")
        void adminCannotUpdateDoctor() throws Exception {
            mockMvc.perform(put("/api/v1/doctors/{id}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new DoctorRequest("Dr. Kwame Asante", "MD-4471", null))));

            verify(doctorService, never()).updateDoctor(any(), any());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a doctor with a blank licence number is rejected as 400")
        void blankLicenceRejected() throws Exception {
            mockMvc.perform(post("/api/v1/doctors").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DoctorRequest("Dr. Kwame Asante", "   ", null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.licenseNumber").exists());

            verify(doctorService, never()).createDoctor(any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller may read neither directory")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/doctors")).andExpect(status().isUnauthorized());
        }
    }
}
