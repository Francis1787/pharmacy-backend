package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Drug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DrugRepository extends JpaRepository<Drug, UUID> {

    List<Drug> findByNameContainingIgnoreCase(String name);

    List<Drug> findByIsControlledSubstanceTrue();

    /**
     * Drugs whose total non-expired batch stock has fallen to or below
     * their reorder_threshold (Rule 8). Powers GET /drugs/below-threshold.
     */
    @Query("""
        SELECT d FROM Drug d
        WHERE (
            SELECT COALESCE(SUM(b.quantityInStock), 0)
            FROM Batch b
            WHERE b.drug = d AND b.expiryDate >= CURRENT_DATE
        ) <= d.reorderThreshold
        """)
    List<Drug> findDrugsBelowReorderThreshold();
}
