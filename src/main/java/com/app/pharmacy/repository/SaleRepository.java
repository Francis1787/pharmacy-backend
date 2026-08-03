package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByPrescriptionId(UUID prescriptionId);

    /** Used to enforce "one prescription, one sale" before insert (Rule 1, Sale.prescription_id UNIQUE). */
    boolean existsByPrescriptionId(UUID prescriptionId);

    List<Sale> findBySaleDateBetween(LocalDateTime from, LocalDateTime to);

    List<Sale> findByDispensingPharmacistId(UUID pharmacistId);
}
