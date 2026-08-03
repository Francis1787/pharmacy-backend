package com.app.pharmacy.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A received stock lot of a given Drug. Batch-level tracking supports
 * expiry enforcement (Rule 5) and stock-decrement-on-sale (Rule 4).
 *
 * purchaseOrderItem and verifiedByPharmacist are both nullable — not every
 * batch traces back to a formal purchase order (e.g. a manual stock
 * correction) — but the service layer must enforce that
 * verifiedByPharmacist is required whenever drug.isControlledSubstance
 * is true (Rule 11) before the batch counts as active/sellable stock.
 */
@Entity
@Table(name = "batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Column(name = "batch_number", nullable = false, length = 50)
    private String batchNumber;

    @Column(name = "quantity_in_stock", nullable = false)
    @Builder.Default
    private Integer quantityInStock = 0;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "date_received", nullable = false)
    private LocalDate dateReceived;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Nullable — links the delivered batch back to the order line item it fulfills. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id")
    private PurchaseOrderItem purchaseOrderItem;

    /** Nullable — pharmacist who confirmed delivery matches the order; mandatory in
     *  practice (enforced at service layer) for controlled substances (Rule 11). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_pharmacist_id")
    private Staff verifiedByPharmacist;

    @PrePersist
    protected void onCreate() {
        if (dateReceived == null) {
            dateReceived = LocalDate.now();
        }
    }

    /** Convenience check used by the dispensing service before a sale (Rule 5). */
    @Transient
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }
}
