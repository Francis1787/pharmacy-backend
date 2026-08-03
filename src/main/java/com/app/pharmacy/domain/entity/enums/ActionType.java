package com.app.pharmacy.domain.entity.enums;

/**
 * Matches the DB CHECK constraint on auditlog.action_type:
 * action_type IN ('Prescription Approved','Prescription Rejected',
 * 'Drug Dispensed','Stock Updated','Purchase Order Created').
 * DB values contain spaces, so dbValue()/fromDbValue() bridge between
 * the enum constant and the raw column string.
 */
public enum ActionType {
    PRESCRIPTION_APPROVED("Prescription Approved"),
    PRESCRIPTION_REJECTED("Prescription Rejected"),
    DRUG_DISPENSED("Drug Dispensed"),
    STOCK_UPDATED("Stock Updated"),
    PURCHASE_ORDER_CREATED("Purchase Order Created");

    private final String dbValue;

    ActionType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static ActionType fromDbValue(String value) {
        for (ActionType type : values()) {
            if (type.dbValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AuditLog action_type: " + value);
    }
}
