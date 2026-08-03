package com.app.pharmacy.domain.entity;

import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A completed sale/transaction against one approved Prescription.
 * prescription is UNIQUE — a prescription can only be fulfilled once
 * (prevents double-dispensing).
 *
 * Per Rule 15, only a Pharmacist may complete a sale, so both cashier
 * and dispensingPharmacist reference Pharmacist-role Staff rows —
 * enforced at the service layer, not by the schema itself.
 */
@Entity
@Table(name = "sale")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, unique = true)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private Staff cashier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispensing_pharmacist_id", nullable = false)
    private Staff dispensingPharmacist;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Stored as the raw DB string (e.g. "Mobile Money") rather than
     * @Enumerated(EnumType.STRING) directly on PaymentMethod, since the enum
     * constant name (MobileMoney) doesn't match the DB value verbatim.
     * Use PaymentMethod.fromDbValue() / dbValue() to convert at the service layer.
     */
    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @PrePersist
    protected void onCreate() {
        if (saleDate == null) {
            saleDate = LocalDateTime.now();
        }
    }
}
