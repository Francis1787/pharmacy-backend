package com.app.pharmacy.domain.entity.enums;

/**
 * Matches the DB CHECK constraint:
 * payment_method IN ('Cash','Mobile Money','Card') on the sale table.
 */
public enum PaymentMethod {
    Cash, MobileMoney, Card;

    /** DB stores "Mobile Money" with a space; map explicitly rather than relying on enum name(). */
    public String dbValue() {
        return this == MobileMoney ? "Mobile Money" : name();
    }

    public static PaymentMethod fromDbValue(String value) {
        return "Mobile Money".equals(value) ? MobileMoney : PaymentMethod.valueOf(value);
    }
}
