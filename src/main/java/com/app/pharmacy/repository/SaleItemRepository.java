package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findBySaleId(UUID saleId);

    List<SaleItem> findByBatchId(UUID batchId);
}
