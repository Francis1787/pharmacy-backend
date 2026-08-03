package com.app.pharmacy.domain.entity;

import com.app.pharmacy.domain.entity.enums.ActionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit trail entry. Written server-side on every prescription
 * approval/rejection, dispensing action, stock update, and purchase-order
 * creation (Rule 7) — never client-supplied.
 */
@Entity
@Table(name = "auditlog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    /**
     * Stored as the raw DB string (e.g. "Prescription Approved") rather than
     * @Enumerated(EnumType.STRING) directly on ActionType, since DB values
     * contain spaces that don't map cleanly to Java enum constant names.
     * Use ActionType.fromDbValue() / dbValue() to convert at the service layer.
     */
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    /** UUID of the affected record (Prescription, Sale, Batch, PurchaseOrder, ...). */
    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    /** Which entity/table the action applied to, e.g. "Prescription", "Sale", "Batch". */
    @Column(name = "reference_table", nullable = false, length = 50)
    private String referenceTable;

    /** Reserved SQL word — quoted in the DDL as "timestamp"; quoted here to match. */
    @Column(name = "\"timestamp\"", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "notes", length = 500)
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
