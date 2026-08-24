package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.BatchCreateRequest;
import com.app.pharmacy.domain.dtos.response.BatchResponse;
import com.app.pharmacy.domain.entity.Batch;
import com.app.pharmacy.domain.entity.Drug;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.Supplier;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.impl.BatchServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Stock receipt and pharmacist verification of deliveries (Rule 11).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BatchService — stock receipt and verification")
class BatchServiceImplTest {

    @Mock private BatchRepository batchRepository;
    @Mock private DrugRepository drugRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private BatchServiceImpl batchService;

    private Drug drug;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        drug = TestFixtures.drug();
        supplier = TestFixtures.supplier();
        when(drugRepository.findById(drug.getId())).thenReturn(Optional.of(drug));
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> {
            Batch b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
    }

    private BatchCreateRequest request(LocalDate expiry) {
        return new BatchCreateRequest(drug.getId(), "BN-2026-001", 250, expiry, supplier.getId(), null);
    }

    @Nested
    @DisplayName("Receiving stock")
    class Receiving {

        @Test
        @DisplayName("creates the batch unverified — verification is a separate deliberate act")
        void createsUnverified() {
            BatchResponse response = batchService.createBatch(request(LocalDate.now().plusYears(2)));

            assertThat(response.verifiedByPharmacist()).isNull();
            assertThat(response.quantityInStock()).isEqualTo(250);
        }

        @Test
        @DisplayName("stamps dateReceived as today rather than trusting the client")
        void stampsDateReceived() {
            ArgumentCaptor<Batch> saved = ArgumentCaptor.forClass(Batch.class);
            batchService.createBatch(request(LocalDate.now().plusYears(2)));
            verify(batchRepository).save(saved.capture());

            assertThat(saved.getValue().getDateReceived()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("404s on an unknown drug")
        void unknownDrug() {
            UUID unknown = UUID.randomUUID();
            when(drugRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> batchService.createBatch(new BatchCreateRequest(
                    unknown, "BN-X", 1, LocalDate.now().plusYears(1), supplier.getId(), null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Drug not found");
        }

        @Test
        @DisplayName("404s on an unknown supplier")
        void unknownSupplier() {
            UUID unknown = UUID.randomUUID();
            when(supplierRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> batchService.createBatch(new BatchCreateRequest(
                    drug.getId(), "BN-X", 1, LocalDate.now().plusYears(1), unknown, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Supplier not found");
        }

        @Test
        @DisplayName("404s on an unknown purchase order item when one is supplied")
        void unknownPurchaseOrderItem() {
            UUID unknown = UUID.randomUUID();
            when(purchaseOrderItemRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> batchService.createBatch(new BatchCreateRequest(
                    drug.getId(), "BN-X", 1, LocalDate.now().plusYears(1), supplier.getId(), unknown)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Purchase order item not found");
        }
    }

    @Nested
    @DisplayName("Rule 11 — only a Pharmacist may verify a delivery")
    class Verification {

        @Test
        @DisplayName("records the verifying pharmacist")
        void pharmacistCanVerify() {
            Staff pharmacist = TestFixtures.pharmacist();
            Batch batch = TestFixtures.batch(drug, 100);
            when(staffRepository.findById(pharmacist.getId())).thenReturn(Optional.of(pharmacist));
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            BatchResponse response = batchService.verifyBatch(batch.getId(), pharmacist.getId());

            assertThat(response.verifiedByPharmacist()).isNotNull();
            assertThat(response.verifiedByPharmacist().id()).isEqualTo(pharmacist.getId());
        }

        @Test
        @DisplayName("refuses verification by a Technician")
        void technicianCannotVerify() {
            Staff technician = TestFixtures.technician();
            Batch batch = TestFixtures.batch(drug, 100);
            when(staffRepository.findById(technician.getId())).thenReturn(Optional.of(technician));
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> batchService.verifyBatch(batch.getId(), technician.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Rule 11");

            assertThat(batch.getVerifiedByPharmacist()).isNull();
            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("refuses verification by an Admin")
        void adminCannotVerify() {
            Staff admin = TestFixtures.admin();
            Batch batch = TestFixtures.batch(drug, 100);
            when(staffRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> batchService.verifyBatch(batch.getId(), admin.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("audits a controlled-substance verification with its own distinct note")
        void auditsControlledSubstanceVerificationDistinctly() {
            Staff pharmacist = TestFixtures.pharmacist();
            Drug controlled = TestFixtures.drug("Morphine", new BigDecimal("60.00"), true);
            Batch batch = TestFixtures.batch(controlled, 40);
            when(staffRepository.findById(pharmacist.getId())).thenReturn(Optional.of(pharmacist));
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
            batchService.verifyBatch(batch.getId(), pharmacist.getId());
            verify(auditLogService).logAction(eq(pharmacist.getId()), eq(ActionType.STOCK_UPDATED),
                    eq(batch.getId()), eq("Batch"), note.capture());

            assertThat(note.getValue()).contains("controlled-substance");
        }

        @Test
        @DisplayName("audits an ordinary verification with the plain note")
        void auditsOrdinaryVerification() {
            Staff pharmacist = TestFixtures.pharmacist();
            Batch batch = TestFixtures.batch(drug, 40);
            when(staffRepository.findById(pharmacist.getId())).thenReturn(Optional.of(pharmacist));
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
            batchService.verifyBatch(batch.getId(), pharmacist.getId());
            verify(auditLogService).logAction(any(), any(), any(), anyString(), note.capture());

            assertThat(note.getValue()).doesNotContain("controlled-substance");
        }

        @Test
        @DisplayName("404s on an unknown batch")
        void unknownBatch() {
            UUID unknown = UUID.randomUUID();
            when(batchRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> batchService.verifyBatch(unknown, UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Batch not found");
        }
    }

    @Nested
    @DisplayName("Expiry reporting (Rule 6)")
    class ExpiryReporting {

        @Test
        @DisplayName("queries the window from today to today+days inclusive")
        void queriesCorrectWindow() {
            when(batchRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of());

            batchService.getBatchesExpiringWithinDays(30);

            ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
            verify(batchRepository).findByExpiryDateBetween(from.capture(), to.capture());

            assertThat(from.getValue()).isEqualTo(LocalDate.now());
            assertThat(to.getValue()).isEqualTo(LocalDate.now().plusDays(30));
        }

        @Test
        @DisplayName("marks a batch already past its expiry as expired in the response")
        void flagsExpiredBatchInResponse() {
            Batch expired = TestFixtures.batch(drug, 5, LocalDate.now().minusDays(3));
            when(batchRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

            BatchResponse response = batchService.getBatchById(expired.getId());

            assertThat(response.isExpired()).isTrue();
        }
    }
}
