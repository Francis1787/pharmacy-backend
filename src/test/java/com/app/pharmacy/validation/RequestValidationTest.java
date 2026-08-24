package com.app.pharmacy.validation;

import com.app.pharmacy.domain.dtos.request.*;
import com.app.pharmacy.domain.entity.enums.DosageForm;
import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation on the request DTOs — the outermost input gate, and the
 * cheapest place to stop bad data. Two of these DTOs carry cross-field
 * {@code @AssertTrue} rules that no amount of field-level annotation would
 * express, and those are the ones most likely to rot unnoticed.
 */
@DisplayName("Request validation — input gate")
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private <T> Set<ConstraintViolation<T>> violations(T dto) {
        return validator.validate(dto);
    }

    private <T> List<String> violatedPaths(T dto) {
        return violations(dto).stream().map(v -> v.getPropertyPath().toString()).toList();
    }

    @Nested
    @DisplayName("StaffCreateRequest — cross-field rules")
    class StaffCreate {

        private StaffCreateRequest of(StaffRole role, String licence, boolean generate, String tempPassword) {
            return new StaffCreateRequest("Abena Darko", role, licence, "+233201234567",
                    "abena@pharmacy.test", LocalDate.now(), generate, tempPassword);
        }

        @Test
        @DisplayName("accepts a Pharmacist carrying a licence number")
        void pharmacistWithLicence() {
            assertThat(violations(of(StaffRole.Pharmacist, "PH-1001", true, null))).isEmpty();
        }

        @Test
        @DisplayName("rejects a Pharmacist with no licence number")
        void pharmacistWithoutLicence() {
            assertThat(violatedPaths(of(StaffRole.Pharmacist, null, true, null)))
                    .contains("licenseNumberValidForRole");
        }

        @Test
        @DisplayName("rejects a Pharmacist whose licence number is only whitespace")
        void pharmacistWithBlankLicence() {
            assertThat(violations(of(StaffRole.Pharmacist, "   ", true, null))).isNotEmpty();
        }

        @Test
        @DisplayName("rejects a Technician who was given a licence number")
        void technicianWithLicence() {
            assertThat(violatedPaths(of(StaffRole.Technician, "PH-1001", true, null)))
                    .contains("licenseNumberValidForRole");
        }

        @Test
        @DisplayName("accepts a Technician with no licence number")
        void technicianWithoutLicence() {
            assertThat(violations(of(StaffRole.Technician, null, true, null))).isEmpty();
        }

        @Test
        @DisplayName("rejects generatePassword=false with no tempPassword supplied")
        void missingTempPassword() {
            assertThat(violatedPaths(of(StaffRole.Technician, null, false, null)))
                    .contains("passwordSetupValid");
        }

        @Test
        @DisplayName("accepts generatePassword=false when a tempPassword is supplied")
        void suppliedTempPassword() {
            assertThat(violations(of(StaffRole.Technician, null, false, "Temp-Pass-1"))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"12345", "not-a-phone", "+233 20 123 4567 8901234567890"})
        @DisplayName("rejects malformed phone numbers")
        void rejectsBadPhoneNumbers(String phone) {
            StaffCreateRequest request = new StaffCreateRequest("Abena Darko", StaffRole.Technician, null,
                    phone, "abena@pharmacy.test", LocalDate.now(), true, null);

            assertThat(violatedPaths(request)).contains("phoneNumber");
        }

        @Test
        @DisplayName("rejects a malformed email")
        void rejectsBadEmail() {
            StaffCreateRequest request = new StaffCreateRequest("Abena Darko", StaffRole.Technician, null,
                    "+233201234567", "not-an-email", LocalDate.now(), true, null);

            assertThat(violatedPaths(request)).contains("email");
        }
    }

    @Nested
    @DisplayName("StaffUpdateRequest")
    class StaffUpdate {

        @Test
        @DisplayName("applies the same licence/role rule as creation")
        void enforcesLicenceRule() {
            StaffUpdateRequest promotingWithoutLicence = new StaffUpdateRequest(
                    "Kofi Boateng", StaffRole.Pharmacist, null, "+233201234567", "kofi@pharmacy.test", true);

            assertThat(violatedPaths(promotingWithoutLicence)).contains("licenseNumberValidForRole");
        }

        @Test
        @DisplayName("requires an explicit activeStatus rather than defaulting silently")
        void requiresActiveStatus() {
            StaffUpdateRequest noStatus = new StaffUpdateRequest(
                    "Kofi Boateng", StaffRole.Technician, null, "+233201234567", "kofi@pharmacy.test", null);

            assertThat(violatedPaths(noStatus)).contains("activeStatus");
        }
    }

    @Nested
    @DisplayName("BatchCreateRequest — Rule 5 starts at intake")
    class BatchCreate {

        private BatchCreateRequest withExpiry(LocalDate expiry) {
            return new BatchCreateRequest(UUID.randomUUID(), "BN-1", 100, expiry, UUID.randomUUID(), null);
        }

        @Test
        @DisplayName("accepts a future expiry date")
        void acceptsFutureExpiry() {
            assertThat(violations(withExpiry(LocalDate.now().plusMonths(6)))).isEmpty();
        }

        @Test
        @DisplayName("refuses to receive stock that is already expired")
        void rejectsPastExpiry() {
            assertThat(violatedPaths(withExpiry(LocalDate.now().minusDays(1)))).contains("expiryDate");
        }

        @Test
        @DisplayName("refuses stock expiring today — @Future excludes the current date")
        void rejectsTodayExpiry() {
            assertThat(violatedPaths(withExpiry(LocalDate.now()))).contains("expiryDate");
        }

        @Test
        @DisplayName("rejects a negative quantity")
        void rejectsNegativeQuantity() {
            BatchCreateRequest negative = new BatchCreateRequest(
                    UUID.randomUUID(), "BN-1", -5, LocalDate.now().plusYears(1), UUID.randomUUID(), null);

            assertThat(violatedPaths(negative)).contains("quantityInStock");
        }

        @Test
        @DisplayName("rejects a blank batch number")
        void rejectsBlankBatchNumber() {
            BatchCreateRequest blank = new BatchCreateRequest(
                    UUID.randomUUID(), "  ", 10, LocalDate.now().plusYears(1), UUID.randomUUID(), null);

            assertThat(violatedPaths(blank)).contains("batchNumber");
        }
    }

    @Nested
    @DisplayName("PrescriptionCreateRequest")
    class PrescriptionCreate {

        private PrescriptionCreateRequest with(LocalDate dateIssued, List<PrescriptionCreateRequest.Item> items) {
            return new PrescriptionCreateRequest(UUID.randomUUID(), UUID.randomUUID(), dateIssued, null, items);
        }

        private final PrescriptionCreateRequest.Item validItem =
                new PrescriptionCreateRequest.Item(UUID.randomUUID(), "1 tablet daily", 30);

        @Test
        @DisplayName("accepts a script issued today")
        void acceptsToday() {
            assertThat(violations(with(LocalDate.now(), List.of(validItem)))).isEmpty();
        }

        @Test
        @DisplayName("rejects a script dated in the future")
        void rejectsFutureIssueDate() {
            assertThat(violatedPaths(with(LocalDate.now().plusDays(1), List.of(validItem))))
                    .contains("dateIssued");
        }

        @Test
        @DisplayName("rejects a prescription with no line items")
        void rejectsEmptyItems() {
            assertThat(violatedPaths(with(LocalDate.now(), List.of()))).contains("items");
        }

        @Test
        @DisplayName("cascades validation into each line item")
        void validatesNestedItems() {
            PrescriptionCreateRequest.Item bad = new PrescriptionCreateRequest.Item(null, "  ", 0);

            assertThat(violatedPaths(with(LocalDate.now(), List.of(bad))))
                    .contains("items[0].drugId", "items[0].dosageInstructions", "items[0].quantityPrescribed");
        }
    }

    @Nested
    @DisplayName("SaleCreateRequest")
    class SaleCreate {

        @Test
        @DisplayName("accepts a minimal valid sale")
        void acceptsValidSale() {
            SaleCreateRequest request = new SaleCreateRequest(UUID.randomUUID(), PaymentMethod.Cash, null, null,
                    List.of(new SaleCreateRequest.Item(UUID.randomUUID(), 1)));

            assertThat(violations(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a sale with no items")
        void rejectsEmptyItems() {
            SaleCreateRequest request = new SaleCreateRequest(
                    UUID.randomUUID(), PaymentMethod.Cash, null, null, List.of());

            assertThat(violatedPaths(request)).contains("items");
        }

        @Test
        @DisplayName("rejects a zero or negative quantity sold")
        void rejectsNonPositiveQuantity() {
            SaleCreateRequest zero = new SaleCreateRequest(UUID.randomUUID(), PaymentMethod.Cash, null, null,
                    List.of(new SaleCreateRequest.Item(UUID.randomUUID(), 0)));
            SaleCreateRequest negative = new SaleCreateRequest(UUID.randomUUID(), PaymentMethod.Cash, null, null,
                    List.of(new SaleCreateRequest.Item(UUID.randomUUID(), -3)));

            assertThat(violatedPaths(zero)).contains("items[0].quantitySold");
            assertThat(violatedPaths(negative)).contains("items[0].quantitySold");
        }

        @Test
        @DisplayName("requires a payment method")
        void requiresPaymentMethod() {
            SaleCreateRequest request = new SaleCreateRequest(UUID.randomUUID(), null, null, null,
                    List.of(new SaleCreateRequest.Item(UUID.randomUUID(), 1)));

            assertThat(violatedPaths(request)).contains("paymentMethod");
        }

        @Test
        @DisplayName("exposes no field for totalAmount or unit price — those are server-computed")
        void carriesNoClientSuppliedPricing() {
            List<String> components = java.util.Arrays.stream(SaleCreateRequest.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName).toList();

            assertThat(components).doesNotContain("totalAmount", "unitPriceAtSale", "unitPrice");
        }
    }

    @Nested
    @DisplayName("ChangePasswordRequest")
    class ChangePassword {

        @Test
        @DisplayName("enforces an 8-character minimum on the new password")
        void enforcesMinimumLength() {
            assertThat(violatedPaths(new ChangePasswordRequest("old-pass", "short"))).contains("newPassword");
            assertThat(violations(new ChangePasswordRequest("old-pass", "just-long"))).isEmpty();
        }

        @Test
        @DisplayName("requires the current password to be supplied")
        void requiresCurrentPassword() {
            assertThat(violatedPaths(new ChangePasswordRequest("", "long-enough-pass"))).contains("currentPassword");
        }
    }

    @Nested
    @DisplayName("Drug and purchase-order money fields")
    class MoneyFields {

        @Test
        @DisplayName("rejects a negative drug unit price")
        void rejectsNegativeUnitPrice() {
            DrugCreateRequest request = new DrugCreateRequest("Paracetamol", "paracetamol",
                    DosageForm.Tablet, "500mg", new BigDecimal("-1.00"), false, 10);

            assertThat(violatedPaths(request)).contains("unitPrice");
        }

        @Test
        @DisplayName("allows a zero price for a give-away line")
        void allowsZeroUnitPrice() {
            DrugCreateRequest request = new DrugCreateRequest("Paracetamol", "paracetamol",
                    DosageForm.Tablet, "500mg", BigDecimal.ZERO, false, 10);

            assertThat(violations(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a negative reorder threshold")
        void rejectsNegativeThreshold() {
            DrugCreateRequest request = new DrugCreateRequest("Paracetamol", "paracetamol",
                    DosageForm.Tablet, "500mg", new BigDecimal("2.00"), false, -1);

            assertThat(violatedPaths(request)).contains("reorderThreshold");
        }

        @Test
        @DisplayName("rejects a purchase order expecting delivery in the past")
        void rejectsPastExpectedDelivery() {
            PurchaseOrderCreateRequest request = new PurchaseOrderCreateRequest(
                    UUID.randomUUID(), LocalDate.now().minusDays(1),
                    List.of(new PurchaseOrderCreateRequest.Item(UUID.randomUUID(), 10, new BigDecimal("1.00"))));

            assertThat(violatedPaths(request)).contains("expectedDeliveryDate");
        }

        @Test
        @DisplayName("rejects a delivery recorded in the future")
        void rejectsFutureActualDelivery() {
            assertThat(violatedPaths(new MarkDeliveredRequest(LocalDate.now().plusDays(1))))
                    .contains("actualDeliveryDate");
        }
    }
}
