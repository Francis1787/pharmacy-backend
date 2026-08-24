package com.app.pharmacy.support;

import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.domain.entity.enums.DosageForm;
import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;
import com.app.pharmacy.domain.entity.enums.StaffRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared entity builders for the unit test suite.
 *
 * Every fixture is deliberately "valid by default" so an individual test
 * only has to mutate the one field it is actually exercising — a test that
 * fails then points at the rule under test rather than at incidental setup.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static Staff pharmacist() {
        return pharmacist("Ama Mensah");
    }

    public static Staff pharmacist(String name) {
        return Staff.builder()
                .id(UUID.randomUUID())
                .fullName(name)
                .role(StaffRole.Pharmacist)
                .licenseNumber("PH-" + UUID.randomUUID().toString().substring(0, 8))
                .phoneNumber("+233200000001")
                .email(name.toLowerCase().replace(' ', '.') + "@pharmacy.test")
                .passwordHash("$2a$10$hash")
                .mustResetPassword(false)
                .hireDate(LocalDate.of(2024, 1, 15))
                .activeStatus(true)
                .build();
    }

    public static Staff technician() {
        return Staff.builder()
                .id(UUID.randomUUID())
                .fullName("Kofi Boateng")
                .role(StaffRole.Technician)
                .licenseNumber(null)
                .phoneNumber("+233200000002")
                .email("kofi.boateng@pharmacy.test")
                .passwordHash("$2a$10$hash")
                .mustResetPassword(false)
                .hireDate(LocalDate.of(2024, 3, 1))
                .activeStatus(true)
                .build();
    }

    public static Staff admin() {
        return Staff.builder()
                .id(UUID.randomUUID())
                .fullName("Yaa Owusu")
                .role(StaffRole.Admin)
                .licenseNumber(null)
                .phoneNumber("+233200000003")
                .email("yaa.owusu@pharmacy.test")
                .passwordHash("$2a$10$hash")
                .mustResetPassword(false)
                .hireDate(LocalDate.of(2023, 6, 1))
                .activeStatus(true)
                .build();
    }

    public static Drug drug(String name, BigDecimal unitPrice, boolean controlled) {
        return Drug.builder()
                .id(UUID.randomUUID())
                .name(name)
                .genericName(name.toLowerCase())
                .dosageForm(DosageForm.Tablet)
                .strength("500mg")
                .unitPrice(unitPrice)
                .isControlledSubstance(controlled)
                .reorderThreshold(10)
                .build();
    }

    public static Drug drug() {
        return drug("Paracetamol", new BigDecimal("2.50"), false);
    }

    public static Supplier supplier() {
        return Supplier.builder()
                .id(UUID.randomUUID())
                .companyName("Accra Medical Supplies")
                .contactPerson("Nii Armah")
                .phoneNumber("+233300000001")
                .email("sales@accramed.test")
                .address("12 Ring Road, Accra")
                .build();
    }

    public static Customer customer() {
        return Customer.builder()
                .id(UUID.randomUUID())
                .fullName("Efua Sarpong")
                .phoneNumber("+233240000001")
                .address("5 Oxford Street, Osu")
                .createdAt(LocalDateTime.now().minusMonths(6))
                .build();
    }

    public static Doctor doctor() {
        return Doctor.builder()
                .id(UUID.randomUUID())
                .fullName("Dr. Kwame Asante")
                .licenseNumber("MD-4471")
                .contactInfo("+233270000001 / k.asante@clinic.test")
                .build();
    }

    /** A batch with plenty of stock and an expiry a year out. */
    public static Batch batch(Drug drug, int quantityInStock) {
        return batch(drug, quantityInStock, LocalDate.now().plusYears(1));
    }

    public static Batch batch(Drug drug, int quantityInStock, LocalDate expiryDate) {
        return Batch.builder()
                .id(UUID.randomUUID())
                .drug(drug)
                .batchNumber("BN-" + UUID.randomUUID().toString().substring(0, 6))
                .quantityInStock(quantityInStock)
                .expiryDate(expiryDate)
                .dateReceived(LocalDate.now().minusDays(30))
                .supplier(supplier())
                .purchaseOrderItem(null)
                .verifiedByPharmacist(null)
                .build();
    }

    public static Prescription prescription(ApprovalStatus status) {
        return Prescription.builder()
                .id(UUID.randomUUID())
                .customer(customer())
                .doctor(doctor())
                .dateIssued(LocalDate.now().minusDays(1))
                .dateReceived(LocalDateTime.now().minusHours(2))
                .approvingPharmacist(status == ApprovalStatus.Pending ? null : pharmacist())
                .approvalStatus(status)
                .notes(null)
                .build();
    }

    public static PurchaseOrder purchaseOrder(Supplier supplier, Staff createdBy, PurchaseOrderStatus status) {
        return PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .supplier(supplier)
                .orderDate(LocalDate.now().minusDays(10))
                .expectedDeliveryDate(LocalDate.now().plusDays(5))
                .actualDeliveryDate(null)
                .status(status)
                .createdBy(createdBy)
                .build();
    }
}
