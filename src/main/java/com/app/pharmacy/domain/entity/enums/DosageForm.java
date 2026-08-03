package com.app.pharmacy.domain.entity.enums;

/**
 * Matches the DB CHECK constraint:
 * dosage_form IN ('Tablet','Syrup','Injection','Capsule','Cream','Other').
 */
public enum DosageForm {
    Tablet, Syrup, Injection, Capsule, Cream, Other
}
