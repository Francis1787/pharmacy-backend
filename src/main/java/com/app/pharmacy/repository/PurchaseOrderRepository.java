package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.PurchaseOrder;
import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    /**
     * Orders past their expected delivery date that haven't actually arrived yet
     * and haven't been cancelled. Powers GET /purchase-orders/overdue (Rule 17).
     * Call as findByActualDeliveryDateIsNullAndExpectedDeliveryDateBeforeAndStatusNot(
     *     LocalDate.now(), PurchaseOrderStatus.Cancelled).
     */
    List<PurchaseOrder> findByActualDeliveryDateIsNullAndExpectedDeliveryDateBeforeAndStatusNot(
            LocalDate today, PurchaseOrderStatus excludedStatus);
}
