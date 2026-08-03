package com.app.pharmacy.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single drug line item within a Sale, drawn from a specific Batch
 * so stock deduction (Rule 4) and expiry tracking (Rule 5) stay batch-accurate.
 * Fully derived server-side from what's dispensed — no standalone create endpoint.
 */
@Entity
@Table(name = "saleitem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(name = "quantity_sold", nullable = false)
    private Integer quantitySold;

    /** Snapshot of Drug.unit_price at the moment of sale, so later price
     *  changes don't retroactively alter historical receipts. */
    @Column(name = "unit_price_at_sale", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceAtSale;
}
