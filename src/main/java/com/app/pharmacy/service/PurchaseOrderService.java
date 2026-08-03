package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.MarkDeliveredRequest;
import com.app.pharmacy.domain.dtos.request.PurchaseOrderCreateRequest;
import com.app.pharmacy.domain.dtos.response.PurchaseOrderResponse;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderService {

    /** Admin only (Rule 16). createdByStaffId is the authenticated caller, not client-supplied. */
    PurchaseOrderResponse createPurchaseOrder(PurchaseOrderCreateRequest request, UUID createdByStaffId);

    /** Admin only. Sets actual_delivery_date and flips status to Received. */
    PurchaseOrderResponse markDelivered(UUID id, MarkDeliveredRequest request);

    PurchaseOrderResponse getPurchaseOrderById(UUID id);

    List<PurchaseOrderResponse> getAllPurchaseOrders();

    /** Rule 17 — orders past expected date, not yet delivered, not cancelled. */
    List<PurchaseOrderResponse> getOverduePurchaseOrders();

    /** Rule 11 + 17 — delivered orders with at least one controlled-substance batch still unverified. */
    List<PurchaseOrderResponse> getPurchaseOrdersAwaitingVerification();
}
