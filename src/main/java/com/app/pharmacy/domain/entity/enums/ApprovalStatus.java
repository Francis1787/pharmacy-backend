package com.app.pharmacy.domain.entity.enums;

/**
 * Matches the DB CHECK constraint:
 * approval_status IN ('Pending','Approved','Rejected') on the prescription table.
 */
public enum ApprovalStatus {
    Pending, Approved, Rejected
}
