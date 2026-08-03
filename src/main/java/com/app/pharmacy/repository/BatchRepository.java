package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByDrugId(UUID drugId);

    List<Batch> findByExpiryDateBefore(LocalDate date);

    List<Batch> findByExpiryDateBetween(LocalDate from, LocalDate to);

    /**
     * Controlled-substance batches delivered against a purchase order but not
     * yet verified by a Pharmacist. Powers Admin's / Pharmacist's
     * GET /purchase-orders/awaiting-verification view (Rule 11, Rule 17).
     */
    @Query("""
        SELECT b FROM Batch b
        WHERE b.drug.isControlledSubstance = true
          AND b.verifiedByPharmacist IS NULL
          AND b.purchaseOrderItem IS NOT NULL
        """)
    List<Batch> findUnverifiedControlledSubstanceBatches();

    /** Sellable batches for a drug: not expired, has stock remaining. */
    @Query("""
        SELECT b FROM Batch b
        WHERE b.drug.id = :drugId
          AND b.expiryDate >= CURRENT_DATE
          AND b.quantityInStock > 0
        ORDER BY b.expiryDate ASC
        """)
    List<Batch> findSellableBatchesByDrugId(UUID drugId);
}
