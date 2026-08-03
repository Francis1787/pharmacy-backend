package com.app.pharmacy.domain.entity.enums;

/**
 * Staff role. Matches the DB CHECK constraint:
 * role IN ('Pharmacist','Technician','Admin').
 * Enum constant names are kept in this exact casing so
 * @Enumerated(EnumType.STRING) round-trips identically against the column.
 */
public enum StaffRole {
    Pharmacist, Technician, Admin
}
