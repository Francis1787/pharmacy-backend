package com.app.pharmacy.domain.entity;

import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A purchase order placed with a Supplier. Admin-only to create (Rule 16).
 *
 * actual_delivery_date is NULL until the delivery physically arrives;
 * it is compared against expected_delivery_date to power Admin's
 * overdue-delivery view (Rule 17).
 */
@Entity
@Table(name = "purchaseorder")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /** NULL = not yet delivered. Set once the shipment physically arrives. */
    @Column(name = "actual_delivery_date")
    private LocalDate actualDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.Pending;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_staff_id", nullable = false)
    private Staff createdBy;

    @PrePersist
    protected void onCreate() {
        if (orderDate == null) {
            orderDate = LocalDate.now();
        }
    }
}
