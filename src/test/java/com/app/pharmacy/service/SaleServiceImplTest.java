package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.SaleCreateRequest;
import com.app.pharmacy.domain.dtos.response.SaleResponse;
import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.impl.SaleServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
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
 * Dispensing is the highest-risk path in the system: it is the only place
 * that moves stock, takes money, and touches controlled substances, and it
 * is where Rules 1, 3, 4, 5 and 15 all have to hold at once.
 *
 * These tests drive {@link SaleServiceImpl} directly with mocked repositories
 * so each rule can be violated in isolation — a DB round-trip would let the
 * UNIQUE/CHECK constraints mask whether the service itself is enforcing
 * anything.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SaleService — dispensing rules")
class SaleServiceImplTest {

    @Mock private SaleRepository saleRepository;
    @Mock private SaleItemRepository saleItemRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private SaleServiceImpl saleService;

    private Staff pharmacist;
    private Prescription approvedPrescription;

    @BeforeEach
    void setUp() {
        pharmacist = TestFixtures.pharmacist();
        approvedPrescription = TestFixtures.prescription(ApprovalStatus.Approved);

        when(staffRepository.findById(pharmacist.getId())).thenReturn(Optional.of(pharmacist));
        when(prescriptionRepository.findById(approvedPrescription.getId()))
                .thenReturn(Optional.of(approvedPrescription));
        when(saleRepository.existsByPrescriptionId(any())).thenReturn(false);
        // save() is identity here so the service's own object graph is what gets asserted.
        when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saleItemRepository.save(any(SaleItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saleItemRepository.findBySaleId(any())).thenReturn(List.of());
    }

    private SaleCreateRequest request(UUID prescriptionId, SaleCreateRequest.Item... items) {
        return new SaleCreateRequest(prescriptionId, PaymentMethod.Cash, null, null, List.of(items));
    }

    @Nested
    @DisplayName("Rule 1 — only an Approved prescription may be dispensed")
    class PrescriptionApprovalGate {

        @Test
        @DisplayName("rejects a Pending prescription")
        void rejectsPendingPrescription() {
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 100);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> saleService.createSale(
                    request(pending.getId(), new SaleCreateRequest.Item(batch.getId(), 1)), pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Pending");

            // No stock may move on a refused dispense.
            verify(batchRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a Rejected prescription")
        void rejectsRejectedPrescription() {
            Prescription rejected = TestFixtures.prescription(ApprovalStatus.Rejected);
            when(prescriptionRepository.findById(rejected.getId())).thenReturn(Optional.of(rejected));

            assertThatThrownBy(() -> saleService.createSale(
                    request(rejected.getId(), new SaleCreateRequest.Item(UUID.randomUUID(), 1)), pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Rejected");
        }

        @Test
        @DisplayName("404s on an unknown prescription id")
        void unknownPrescription() {
            UUID unknown = UUID.randomUUID();
            when(prescriptionRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> saleService.createSale(
                    request(unknown, new SaleCreateRequest.Item(UUID.randomUUID(), 1)), pharmacist.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("refuses to dispense the same prescription twice")
        void refusesDoubleDispensing() {
            when(saleRepository.existsByPrescriptionId(approvedPrescription.getId())).thenReturn(true);

            assertThatThrownBy(() -> saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(UUID.randomUUID(), 1)),
                    pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already been dispensed");
        }
    }

    @Nested
    @DisplayName("Rule 5 — expired stock can never be dispensed")
    class ExpiryGate {

        @Test
        @DisplayName("rejects a batch that expired yesterday")
        void rejectsExpiredBatch() {
            Batch expired = TestFixtures.batch(TestFixtures.drug(), 100, LocalDate.now().minusDays(1));
            when(batchRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(expired.getId(), 1)),
                    pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Rule 5");

            assertThat(expired.getQuantityInStock())
                    .as("stock must not be decremented when the sale is refused")
                    .isEqualTo(100);
        }

        @Test
        @DisplayName("allows a batch expiring today — expiry is end-of-day, not start")
        void allowsBatchExpiringToday() {
            Batch expiringToday = TestFixtures.batch(TestFixtures.drug(), 10, LocalDate.now());
            when(batchRepository.findById(expiringToday.getId())).thenReturn(Optional.of(expiringToday));

            SaleResponse response = saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(expiringToday.getId(), 2)),
                    pharmacist.getId());

            assertThat(response).isNotNull();
            assertThat(expiringToday.getQuantityInStock()).isEqualTo(8);
        }

        @Test
        @DisplayName("rejects the whole sale when a later line item is expired")
        void rejectsWholeSaleIfAnyItemExpired() {
            Batch good = TestFixtures.batch(TestFixtures.drug(), 50);
            Batch expired = TestFixtures.batch(TestFixtures.drug(), 50, LocalDate.now().minusMonths(2));
            when(batchRepository.findById(good.getId())).thenReturn(Optional.of(good));
            when(batchRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> saleService.createSale(
                    request(approvedPrescription.getId(),
                            new SaleCreateRequest.Item(good.getId(), 5),
                            new SaleCreateRequest.Item(expired.getId(), 5)),
                    pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class);

            // The first item was already decremented in-memory before the second threw.
            // The @Transactional rollback is what undoes it — this assertion documents
            // that the service itself does NOT pre-validate every line before mutating,
            // so the transaction boundary is load-bearing and must not be removed.
            assertThat(good.getQuantityInStock()).isEqualTo(45);
        }
    }

    @Nested
    @DisplayName("Rule 4 — stock decrements automatically at dispense time")
    class StockDecrement {

        @Test
        @DisplayName("decrements each batch by exactly the quantity sold")
        void decrementsStock() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 40);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(batch.getId(), 12)),
                    pharmacist.getId());

            assertThat(batch.getQuantityInStock()).isEqualTo(28);
            verify(batchRepository).save(batch);
        }

        @Test
        @DisplayName("refuses to sell more than the batch holds")
        void refusesOversell() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 3);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(batch.getId(), 4)),
                    pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Insufficient stock");

            assertThat(batch.getQuantityInStock()).isEqualTo(3);
        }

        @Test
        @DisplayName("allows selling a batch down to exactly zero")
        void allowsExactStockSale() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 7);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(batch.getId(), 7)),
                    pharmacist.getId());

            assertThat(batch.getQuantityInStock()).isZero();
        }

        @Test
        @DisplayName("404s on an unknown batch id")
        void unknownBatch() {
            UUID unknown = UUID.randomUUID();
            when(batchRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(unknown, 1)),
                    pharmacist.getId()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Batch not found");
        }
    }

    @Nested
    @DisplayName("Rule 15 — only a Pharmacist may take payment or dispense")
    class PharmacistOnly {

        @Test
        @DisplayName("rejects a Technician named as dispensing pharmacist")
        void rejectsTechnicianDispenser() {
            Staff technician = TestFixtures.technician();
            when(staffRepository.findById(technician.getId())).thenReturn(Optional.of(technician));

            SaleCreateRequest req = new SaleCreateRequest(
                    approvedPrescription.getId(), PaymentMethod.Cash,
                    null, technician.getId(),
                    List.of(new SaleCreateRequest.Item(UUID.randomUUID(), 1)));

            assertThatThrownBy(() -> saleService.createSale(req, pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Rule 15");
        }

        @Test
        @DisplayName("rejects an Admin named as cashier")
        void rejectsAdminCashier() {
            Staff admin = TestFixtures.admin();
            when(staffRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

            SaleCreateRequest req = new SaleCreateRequest(
                    approvedPrescription.getId(), PaymentMethod.Cash,
                    admin.getId(), null,
                    List.of(new SaleCreateRequest.Item(UUID.randomUUID(), 1)));

            assertThatThrownBy(() -> saleService.createSale(req, pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Rule 15");
        }

        @Test
        @DisplayName("allows a split cashier / dispenser when both are Pharmacists")
        void allowsSplitPharmacistRoles() {
            Staff second = TestFixtures.pharmacist("Adwoa Nyarko");
            when(staffRepository.findById(second.getId())).thenReturn(Optional.of(second));
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 20);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            SaleCreateRequest req = new SaleCreateRequest(
                    approvedPrescription.getId(), PaymentMethod.Cash,
                    second.getId(), pharmacist.getId(),
                    List.of(new SaleCreateRequest.Item(batch.getId(), 1)));

            ArgumentCaptor<Sale> saved = ArgumentCaptor.forClass(Sale.class);
            saleService.createSale(req, pharmacist.getId());
            verify(saleRepository, atLeastOnce()).save(saved.capture());

            assertThat(saved.getValue().getCashier().getId()).isEqualTo(second.getId());
            assertThat(saved.getValue().getDispensingPharmacist().getId()).isEqualTo(pharmacist.getId());
        }

        @Test
        @DisplayName("defaults both roles to the authenticated caller when omitted")
        void defaultsToAuthenticatedStaff() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 20);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            ArgumentCaptor<Sale> saved = ArgumentCaptor.forClass(Sale.class);
            saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(batch.getId(), 1)),
                    pharmacist.getId());
            verify(saleRepository, atLeastOnce()).save(saved.capture());

            assertThat(saved.getValue().getCashier().getId()).isEqualTo(pharmacist.getId());
            assertThat(saved.getValue().getDispensingPharmacist().getId()).isEqualTo(pharmacist.getId());
        }
    }

    @Nested
    @DisplayName("Pricing — totals are computed server-side, never accepted from the client")
    class Pricing {

        @Test
        @DisplayName("snapshots the drug's current unit price onto each line item")
        void snapshotsUnitPrice() {
            Drug drug = TestFixtures.drug("Amoxicillin", new BigDecimal("4.75"), false);
            Batch batch = TestFixtures.batch(drug, 30);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            ArgumentCaptor<SaleItem> item = ArgumentCaptor.forClass(SaleItem.class);
            saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(batch.getId(), 3)),
                    pharmacist.getId());
            verify(saleItemRepository).save(item.capture());

            assertThat(item.getValue().getUnitPriceAtSale()).isEqualByComparingTo("4.75");
            assertThat(item.getValue().getQuantitySold()).isEqualTo(3);
        }

        @Test
        @DisplayName("totals multiple line items at their own prices")
        void totalsMultipleItems() {
            Batch cheap = TestFixtures.batch(TestFixtures.drug("Paracetamol", new BigDecimal("2.50"), false), 100);
            Batch dear = TestFixtures.batch(TestFixtures.drug("Insulin", new BigDecimal("120.00"), false), 100);
            when(batchRepository.findById(cheap.getId())).thenReturn(Optional.of(cheap));
            when(batchRepository.findById(dear.getId())).thenReturn(Optional.of(dear));

            ArgumentCaptor<Sale> saved = ArgumentCaptor.forClass(Sale.class);
            saleService.createSale(
                    request(approvedPrescription.getId(),
                            new SaleCreateRequest.Item(cheap.getId(), 4),   //  10.00
                            new SaleCreateRequest.Item(dear.getId(), 2)),   // 240.00
                    pharmacist.getId());
            verify(saleRepository, atLeastOnce()).save(saved.capture());

            assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("250.00");
        }

        @Test
        @DisplayName("stores 'Mobile Money' as the DB spelling, not the enum constant name")
        void mapsPaymentMethodToDbValue() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 10);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            SaleCreateRequest req = new SaleCreateRequest(
                    approvedPrescription.getId(), PaymentMethod.MobileMoney, null, null,
                    List.of(new SaleCreateRequest.Item(batch.getId(), 1)));

            ArgumentCaptor<Sale> saved = ArgumentCaptor.forClass(Sale.class);
            saleService.createSale(req, pharmacist.getId());
            verify(saleRepository, atLeastOnce()).save(saved.capture());

            assertThat(saved.getValue().getPaymentMethod()).isEqualTo("Mobile Money");
        }
    }

    @Nested
    @DisplayName("Rules 3 & 7 — every dispense is audited, controlled substances distinctly so")
    class Auditing {

        @Test
        @DisplayName("logs DRUG_DISPENSED against the dispensing pharmacist")
        void logsDispense() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 10);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(batch.getId(), 1)),
                    pharmacist.getId());

            verify(auditLogService).logAction(
                    eq(pharmacist.getId()), eq(ActionType.DRUG_DISPENSED), any(UUID.class), eq("Sale"), anyString());
        }

        @Test
        @DisplayName("flags the audit note when any line item is a controlled substance")
        void flagsControlledSubstance() {
            Batch ordinary = TestFixtures.batch(TestFixtures.drug("Paracetamol", new BigDecimal("2.50"), false), 50);
            Batch controlled = TestFixtures.batch(TestFixtures.drug("Morphine", new BigDecimal("60.00"), true), 50);
            when(batchRepository.findById(ordinary.getId())).thenReturn(Optional.of(ordinary));
            when(batchRepository.findById(controlled.getId())).thenReturn(Optional.of(controlled));

            ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
            saleService.createSale(
                    request(approvedPrescription.getId(),
                            new SaleCreateRequest.Item(ordinary.getId(), 1),
                            new SaleCreateRequest.Item(controlled.getId(), 1)),
                    pharmacist.getId());
            verify(auditLogService).logAction(any(), any(), any(), anyString(), note.capture());

            assertThat(note.getValue()).contains("controlled substance");
        }

        @Test
        @DisplayName("uses the plain note when no controlled substance is involved")
        void plainNoteForOrdinaryDispense() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 10);
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
            saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(batch.getId(), 1)),
                    pharmacist.getId());
            verify(auditLogService).logAction(any(), any(), any(), anyString(), note.capture());

            assertThat(note.getValue()).doesNotContain("controlled substance");
        }
    }

    @Nested
    @DisplayName("Rule 11 — controlled-substance stock must be pharmacist-verified before it is sellable")
    class ControlledSubstanceVerification {

        /**
         * DEFECT-01. Batch's own contract states the service layer "must enforce
         * that verifiedByPharmacist is required whenever drug.isControlledSubstance
         * is true (Rule 11) before the batch counts as active/sellable stock", but
         * createSale() never reads verifiedByPharmacist. An unverified controlled
         * batch dispenses cleanly today.
         *
         * This test asserts the REQUIRED behaviour and therefore fails until the
         * check is added — see the QA report. Do not weaken it to match the code.
         */
        @Test
        @Tag("known-defect")
        @DisplayName("rejects dispensing an unverified controlled-substance batch")
        void rejectsUnverifiedControlledBatch() {
            Batch controlled = TestFixtures.batch(TestFixtures.drug("Pethidine", new BigDecimal("80.00"), true), 25);
            controlled.setVerifiedByPharmacist(null);
            when(batchRepository.findById(controlled.getId())).thenReturn(Optional.of(controlled));

            assertThatThrownBy(() -> saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(controlled.getId(), 1)),
                    pharmacist.getId()))
                    .as("Rule 11: unverified controlled stock must not leave the shelf")
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("allows dispensing once a pharmacist has verified the batch")
        void allowsVerifiedControlledBatch() {
            Batch controlled = TestFixtures.batch(TestFixtures.drug("Pethidine", new BigDecimal("80.00"), true), 25);
            controlled.setVerifiedByPharmacist(TestFixtures.pharmacist("Verifier Pharmacist"));
            when(batchRepository.findById(controlled.getId())).thenReturn(Optional.of(controlled));

            SaleResponse response = saleService.createSale(
                    request(approvedPrescription.getId(), new SaleCreateRequest.Item(controlled.getId(), 2)),
                    pharmacist.getId());

            assertThat(response).isNotNull();
            assertThat(controlled.getQuantityInStock()).isEqualTo(23);
        }
    }
}
