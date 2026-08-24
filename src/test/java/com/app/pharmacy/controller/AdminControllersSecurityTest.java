package com.app.pharmacy.controller;

import com.app.pharmacy.config.PasswordEncoderConfig;
import com.app.pharmacy.config.SecurityConfig;
import com.app.pharmacy.domain.dtos.request.MarkDeliveredRequest;
import com.app.pharmacy.domain.dtos.request.PurchaseOrderCreateRequest;
import com.app.pharmacy.domain.dtos.request.StaffCreateRequest;
import com.app.pharmacy.domain.dtos.request.StaffUpdateRequest;
import com.app.pharmacy.domain.dtos.request.SupplierRequest;
import com.app.pharmacy.domain.dtos.response.*;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.security.*;
import com.app.pharmacy.service.AuditLogService;
import com.app.pharmacy.service.PurchaseOrderService;
import com.app.pharmacy.service.StaffService;
import com.app.pharmacy.service.SupplierService;
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

import java.math.BigDecimal;
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
 * The four Admin-governed surfaces: the audit log, staff accounts,
 * procurement, and the supplier directory (Rules 13, 16, and the audit
 * requirements of Rule 7).
 *
 * <p><b>On what these tests assert for a denied request.</b> They assert that
 * the service was never invoked — the security <i>decision</i> — rather than
 * asserting a 403 status. DEFECT-06 means every {@code @PreAuthorize} denial
 * currently surfaces as 500, so a status assertion here would fail for a
 * reason that has nothing to do with these controllers. That status-code
 * contract is already pinned, once, by the tagged tests in
 * {@code DrugControllerSecurityTest} and {@code SaleControllerSecurityTest};
 * repeating it in every controller would add thirty identical failures to the
 * defect list without adding information.
 *
 * <p>The distinction matters: these tests verify that unauthorised callers
 * <i>cannot act</i>, which is true today. The tagged tests verify that they
 * are <i>told</i> so correctly, which is not.
 */
@WebMvcTest({AuditLogController.class, StaffController.class,
        PurchaseOrderController.class, SupplierController.class})
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:8081",
        "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-for-hs256",
        "app.jwt.expiration-ms=3600000"
})
@DisplayName("Admin-governed controllers — access control")
class AdminControllersSecurityTest {

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @MockitoBean private AuditLogService auditLogService;
    @MockitoBean private StaffService staffService;
    @MockitoBean private PurchaseOrderService purchaseOrderService;
    @MockitoBean private SupplierService supplierService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private Staff admin;

    @BeforeEach
    void setUp() {
        admin = TestFixtures.admin();
    }

    private RequestPostProcessor asAdmin() {
        return user(new CustomUserDetails(admin));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ─────────────────────────── Audit log ───────────────────────────

    @Nested
    @DisplayName("AuditLogController — Admin only (Rule 7)")
    class AuditLog {

        private AuditLogResponse entry() {
            return new AuditLogResponse(UUID.randomUUID(),
                    new com.app.pharmacy.domain.dtos.common.RefSummary(UUID.randomUUID(), "Ama Mensah"),
                    "Drug Dispensed", UUID.randomUUID(), "Sale", LocalDateTime.now(), "Dispensed sale");
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin may read the audit trail")
        void adminMayRead() throws Exception {
            when(auditLogService.getAllAuditLogs()).thenReturn(List.of(entry()));

            mockMvc.perform(get("/api/v1/audit-logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].actionType").value("Drug Dispensed"));
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist cannot read the audit trail")
        void pharmacistCannotRead() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs"));

            verify(auditLogService, never()).getAllAuditLogs();
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician cannot read the audit trail")
        void technicianCannotRead() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs"));

            verify(auditLogService, never()).getAllAuditLogs();
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller gets 401")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs")).andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("routes a staffId filter to the by-staff query")
        void routesStaffFilter() throws Exception {
            UUID staffId = UUID.randomUUID();
            when(auditLogService.getAuditLogsByStaff(staffId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/audit-logs").param("staffId", staffId.toString()))
                    .andExpect(status().isOk());

            verify(auditLogService).getAuditLogsByStaff(staffId);
            verify(auditLogService, never()).getAllAuditLogs();
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("routes a referenceTable/referenceId pair to the by-reference query")
        void routesReferenceFilter() throws Exception {
            UUID referenceId = UUID.randomUUID();
            when(auditLogService.getAuditLogsByReference("Sale", referenceId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/audit-logs")
                            .param("referenceTable", "Sale")
                            .param("referenceId", referenceId.toString()))
                    .andExpect(status().isOk());

            verify(auditLogService).getAuditLogsByReference("Sale", referenceId);
        }
    }

    // ─────────────────────────── Staff ───────────────────────────

    @Nested
    @DisplayName("StaffController — Admin only (Rule 13)")
    class StaffAccounts {

        private StaffResponse staffResponse() {
            return new StaffResponse(UUID.randomUUID(), "Kojo Antwi", StaffRole.Technician, null,
                    "+233201234567", "kojo.antwi@pharmacy.test", true, LocalDate.now(), true);
        }

        private StaffCreateRequest createRequest() {
            return new StaffCreateRequest("Kojo Antwi", StaffRole.Technician, null,
                    "+233201234567", "kojo.antwi@pharmacy.test", LocalDate.now(), true, null);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin may create a staff account and receives the temp password once")
        void adminMayCreateStaff() throws Exception {
            when(staffService.createStaff(any()))
                    .thenReturn(new StaffCreateResponse(staffResponse(), "Temp-Pass-12"));

            mockMvc.perform(post("/api/v1/staff")
                            .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.tempPassword").value("Temp-Pass-12"))
                    .andExpect(jsonPath("$.data.staff.mustResetPassword").value(true));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("the response never carries a password hash")
        void responseHasNoPasswordHash() throws Exception {
            when(staffService.getAllStaff()).thenReturn(List.of(staffResponse()));

            mockMvc.perform(get("/api/v1/staff"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist cannot create a staff account")
        void pharmacistCannotCreateStaff() throws Exception {
            mockMvc.perform(post("/api/v1/staff")
                    .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())));

            verify(staffService, never()).createStaff(any());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician cannot list staff")
        void technicianCannotListStaff() throws Exception {
            mockMvc.perform(get("/api/v1/staff"));

            verify(staffService, never()).getAllStaff();
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist cannot deactivate an account")
        void pharmacistCannotDeactivate() throws Exception {
            mockMvc.perform(patch("/api/v1/staff/{id}/deactivate", UUID.randomUUID()));

            verify(staffService, never()).deactivateStaff(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin may deactivate an account")
        void adminMayDeactivate() throws Exception {
            UUID id = UUID.randomUUID();
            when(staffService.deactivateStaff(id)).thenReturn(staffResponse());

            mockMvc.perform(patch("/api/v1/staff/{id}/deactivate", id)).andExpect(status().isOk());

            verify(staffService).deactivateStaff(id);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("a Pharmacist update with no licence number is rejected as 400")
        void validationRejectsPharmacistWithoutLicence() throws Exception {
            StaffUpdateRequest invalid = new StaffUpdateRequest("Kojo Antwi", StaffRole.Pharmacist, null,
                    "+233201234567", "kojo.antwi@pharmacy.test", true);

            mockMvc.perform(put("/api/v1/staff/{id}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                    .andExpect(status().isBadRequest());

            verify(staffService, never()).updateStaff(any(), any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller gets 401")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/staff")).andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────── Purchase orders ───────────────────────

    @Nested
    @DisplayName("PurchaseOrderController — Admin only (Rule 16)")
    class PurchaseOrders {

        private PurchaseOrderResponse orderResponse() {
            return new PurchaseOrderResponse(UUID.randomUUID(),
                    new com.app.pharmacy.domain.dtos.common.RefSummary(UUID.randomUUID(), "Accra Medical Supplies"),
                    LocalDate.now(), LocalDate.now().plusDays(7), null,
                    PurchaseOrderStatus.Pending,
                    new com.app.pharmacy.domain.dtos.common.RefSummary(admin.getId(), admin.getFullName()),
                    false, List.of());
        }

        private PurchaseOrderCreateRequest createRequest() {
            return new PurchaseOrderCreateRequest(UUID.randomUUID(), LocalDate.now().plusDays(7),
                    List.of(new PurchaseOrderCreateRequest.Item(UUID.randomUUID(), 100, new BigDecimal("1.80"))));
        }

        @Test
        @DisplayName("Admin may raise a purchase order, attributed to the token's staff id")
        void adminMayCreateOrder() throws Exception {
            when(purchaseOrderService.createPurchaseOrder(any(), any())).thenReturn(orderResponse());

            mockMvc.perform(post("/api/v1/purchase-orders").with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())))
                    .andExpect(status().isCreated());

            verify(purchaseOrderService).createPurchaseOrder(any(), eq(admin.getId()));
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist cannot raise a purchase order")
        void pharmacistCannotCreateOrder() throws Exception {
            mockMvc.perform(post("/api/v1/purchase-orders")
                    .contentType(MediaType.APPLICATION_JSON).content(json(createRequest())));

            verify(purchaseOrderService, never()).createPurchaseOrder(any(), any());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician cannot mark an order delivered")
        void technicianCannotMarkDelivered() throws Exception {
            mockMvc.perform(patch("/api/v1/purchase-orders/{id}/mark-delivered", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new MarkDeliveredRequest(LocalDate.now()))));

            verify(purchaseOrderService, never()).markDelivered(any(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("a future delivery date is rejected as 400")
        void futureDeliveryDateRejected() throws Exception {
            mockMvc.perform(patch("/api/v1/purchase-orders/{id}/mark-delivered", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new MarkDeliveredRequest(LocalDate.now().plusDays(1)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.actualDeliveryDate").exists());

            verify(purchaseOrderService, never()).markDelivered(any(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin may read the overdue report (Rule 17)")
        void adminMayReadOverdue() throws Exception {
            when(purchaseOrderService.getOverduePurchaseOrders()).thenReturn(List.of(orderResponse()));

            mockMvc.perform(get("/api/v1/purchase-orders/overdue")).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("awaiting-verification is open to Pharmacists as well as Admin (Rule 11)")
        void pharmacistMayReadAwaitingVerification() throws Exception {
            when(purchaseOrderService.getPurchaseOrdersAwaitingVerification()).thenReturn(List.of(orderResponse()));

            mockMvc.perform(get("/api/v1/purchase-orders/awaiting-verification"))
                    .andExpect(status().isOk());

            verify(purchaseOrderService).getPurchaseOrdersAwaitingVerification();
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician cannot read awaiting-verification")
        void technicianCannotReadAwaitingVerification() throws Exception {
            mockMvc.perform(get("/api/v1/purchase-orders/awaiting-verification"));

            verify(purchaseOrderService, never()).getPurchaseOrdersAwaitingVerification();
        }
    }

    // ─────────────────────────── Suppliers ───────────────────────────

    @Nested
    @DisplayName("SupplierController — Admin writes, Pharmacist reads")
    class Suppliers {

        private SupplierResponse supplierResponse() {
            return new SupplierResponse(UUID.randomUUID(), "Accra Medical Supplies", "Nii Armah",
                    "+233300000001", "sales@accramed.test", "12 Ring Road, Accra");
        }

        private SupplierRequest request() {
            return new SupplierRequest("Accra Medical Supplies", "Nii Armah",
                    "+233300000001", "sales@accramed.test", "12 Ring Road, Accra");
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin may add a supplier")
        void adminMayCreate() throws Exception {
            when(supplierService.createSupplier(any())).thenReturn(supplierResponse());

            mockMvc.perform(post("/api/v1/suppliers")
                            .contentType(MediaType.APPLICATION_JSON).content(json(request())))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist may read suppliers but not add one")
        void pharmacistReadsButCannotWrite() throws Exception {
            when(supplierService.getAllSuppliers()).thenReturn(List.of(supplierResponse()));

            mockMvc.perform(get("/api/v1/suppliers")).andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/suppliers")
                    .contentType(MediaType.APPLICATION_JSON).content(json(request())));

            verify(supplierService).getAllSuppliers();
            verify(supplierService, never()).createSupplier(any());
        }

        @Test
        @WithMockUser(roles = "TECHNICIAN")
        @DisplayName("a Technician cannot even read the supplier directory")
        void technicianCannotRead() throws Exception {
            mockMvc.perform(get("/api/v1/suppliers"));

            verify(supplierService, never()).getAllSuppliers();
        }

        @Test
        @WithMockUser(roles = "PHARMACIST")
        @DisplayName("a Pharmacist cannot update a supplier")
        void pharmacistCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/suppliers/{id}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request())));

            verify(supplierService, never()).updateSupplier(any(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("a malformed supplier email is rejected as 400")
        void malformedEmailRejected() throws Exception {
            SupplierRequest invalid = new SupplierRequest("Accra Medical Supplies", "Nii Armah",
                    "+233300000001", "not-an-email", "12 Ring Road");

            mockMvc.perform(post("/api/v1/suppliers")
                            .contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(supplierService, never()).createSupplier(any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("an unauthenticated caller gets 401")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/suppliers")).andExpect(status().isUnauthorized());
        }
    }
}
