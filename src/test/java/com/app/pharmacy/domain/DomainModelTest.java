package com.app.pharmacy.domain;

import com.app.pharmacy.domain.entity.Batch;
import com.app.pharmacy.domain.entity.Sale;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.domain.entity.enums.DosageForm;
import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The enums in this codebase carry a hidden contract: their constant names
 * must line up with the DB CHECK constraints in V1__init_schema_and_seed_data.sql,
 * and two of them ({@link PaymentMethod}, {@link ActionType}) map to DB strings
 * that differ from the constant name. A silent mismatch there is a runtime
 * constraint violation on insert, not a compile error — which is exactly the
 * kind of thing a test should catch first.
 */
@DisplayName("Domain model — persistence contracts")
class DomainModelTest {

    @Nested
    @DisplayName("Batch expiry (Rule 5)")
    class BatchExpiry {

        @Test
        @DisplayName("yesterday's expiry counts as expired")
        void pastIsExpired() {
            assertThat(TestFixtures.batch(TestFixtures.drug(), 10, LocalDate.now().minusDays(1)).isExpired()).isTrue();
        }

        @Test
        @DisplayName("today's expiry does NOT count as expired — the batch is usable all day")
        void todayIsNotExpired() {
            assertThat(TestFixtures.batch(TestFixtures.drug(), 10, LocalDate.now()).isExpired()).isFalse();
        }

        @Test
        @DisplayName("tomorrow's expiry is not expired")
        void futureIsNotExpired() {
            assertThat(TestFixtures.batch(TestFixtures.drug(), 10, LocalDate.now().plusDays(1)).isExpired()).isFalse();
        }

        @Test
        @DisplayName("a null expiry is treated as not expired rather than throwing")
        void nullExpiryIsNotExpired() {
            Batch batch = TestFixtures.batch(TestFixtures.drug(), 10);
            batch.setExpiryDate(null);

            assertThat(batch.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("PaymentMethod ↔ DB string")
    class PaymentMethodMapping {

        @ParameterizedTest(name = "{0} ↔ \"{1}\"")
        @CsvSource({
                "Cash,        Cash",
                "MobileMoney, Mobile Money",
                "Card,        Card"
        })
        @DisplayName("maps each constant to the exact DB CHECK value")
        void mapsToDbValue(PaymentMethod method, String dbValue) {
            assertThat(method.dbValue()).isEqualTo(dbValue);
            assertThat(PaymentMethod.fromDbValue(dbValue)).isEqualTo(method);
        }

        @ParameterizedTest
        @EnumSource(PaymentMethod.class)
        @DisplayName("round-trips every constant without loss")
        void roundTrips(PaymentMethod method) {
            assertThat(PaymentMethod.fromDbValue(method.dbValue())).isEqualTo(method);
        }

        @Test
        @DisplayName("rejects a value the DB constraint would not permit")
        void rejectsUnknownValue() {
            assertThatThrownBy(() -> PaymentMethod.fromDbValue("Bitcoin"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ActionType ↔ DB string")
    class ActionTypeMapping {

        @ParameterizedTest(name = "{0} ↔ \"{1}\"")
        @CsvSource({
                "PRESCRIPTION_APPROVED, Prescription Approved",
                "PRESCRIPTION_REJECTED, Prescription Rejected",
                "DRUG_DISPENSED,        Drug Dispensed",
                "STOCK_UPDATED,         Stock Updated",
                "PURCHASE_ORDER_CREATED, Purchase Order Created"
        })
        @DisplayName("maps each constant to the exact auditlog CHECK value")
        void mapsToDbValue(ActionType type, String dbValue) {
            assertThat(type.dbValue()).isEqualTo(dbValue);
            assertThat(ActionType.fromDbValue(dbValue)).isEqualTo(type);
        }

        @ParameterizedTest
        @EnumSource(ActionType.class)
        @DisplayName("round-trips every constant without loss")
        void roundTrips(ActionType type) {
            assertThat(ActionType.fromDbValue(type.dbValue())).isEqualTo(type);
        }

        @Test
        @DisplayName("names the offending value when it is not a permitted action type")
        void rejectsUnknownValue() {
            assertThatThrownBy(() -> ActionType.fromDbValue("Drug Deleted"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Drug Deleted");
        }
    }

    @Nested
    @DisplayName("Enum constants match their DB CHECK constraints verbatim")
    class ConstraintAlignment {

        @Test
        @DisplayName("StaffRole — role IN ('Pharmacist','Technician','Admin')")
        void staffRoleNames() {
            assertThat(StaffRole.values()).extracting(Enum::name)
                    .containsExactly("Pharmacist", "Technician", "Admin");
        }

        @Test
        @DisplayName("ApprovalStatus — approval_status IN ('Pending','Approved','Rejected')")
        void approvalStatusNames() {
            assertThat(ApprovalStatus.values()).extracting(Enum::name)
                    .containsExactly("Pending", "Approved", "Rejected");
        }

        @Test
        @DisplayName("PurchaseOrderStatus — status IN ('Pending','Received','Cancelled')")
        void purchaseOrderStatusNames() {
            assertThat(PurchaseOrderStatus.values()).extracting(Enum::name)
                    .containsExactly("Pending", "Received", "Cancelled");
        }

        @Test
        @DisplayName("DosageForm — dosage_form IN ('Tablet','Syrup','Injection','Capsule','Cream','Other')")
        void dosageFormNames() {
            assertThat(DosageForm.values()).extracting(Enum::name)
                    .containsExactly("Tablet", "Syrup", "Injection", "Capsule", "Cream", "Other");
        }
    }

    @Nested
    @DisplayName("@PrePersist defaults")
    class PrePersistDefaults {

        @Test
        @DisplayName("a Sale with no explicit saleDate is stamped on persist")
        void saleDateDefaulted() throws Exception {
            Sale sale = Sale.builder().saleDate(null).build();
            invokeOnCreate(sale);

            assertThat(sale.getSaleDate()).isNotNull();
        }

        @Test
        @DisplayName("a Batch with no explicit dateReceived is stamped on persist")
        void dateReceivedDefaulted() throws Exception {
            Batch batch = Batch.builder().dateReceived(null).build();
            invokeOnCreate(batch);

            assertThat(batch.getDateReceived()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("an explicitly supplied saleDate is left alone")
        void explicitSaleDateKept() throws Exception {
            java.time.LocalDateTime backdated = java.time.LocalDateTime.now().minusDays(2);
            Sale sale = Sale.builder().saleDate(backdated).build();
            invokeOnCreate(sale);

            assertThat(sale.getSaleDate()).isEqualTo(backdated);
        }

        /** The @PrePersist hooks are protected; JPA calls them reflectively and so do we. */
        private void invokeOnCreate(Object entity) throws Exception {
            var method = entity.getClass().getDeclaredMethod("onCreate");
            method.setAccessible(true);
            method.invoke(entity);
        }
    }
}
