package com.app.pharmacy.domain.entity.enums;

/**
 * Matches the DB CHECK constraint:
 * status IN ('Pending','Received','Cancelled') on the purchaseorder table.
 */
public enum PurchaseOrderStatus {
    Pending, Received, Cancelled
}
