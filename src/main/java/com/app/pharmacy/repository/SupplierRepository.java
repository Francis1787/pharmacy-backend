package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByCompanyName(String companyName);
}
