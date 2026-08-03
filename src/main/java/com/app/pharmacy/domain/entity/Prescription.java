package com.app.pharmacy.domain.entity;

import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A physical prescription presented by a Customer. Must be Approved by a
 * Pharmacist (Rule 2) before any linked Sale can be created (Rule 1).
 */
@Entity
@Table(name = "prescription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "date_issued", nullable = false)
    private LocalDate dateIssued;

    @Column(name = "date_received", nullable = false)
    private LocalDateTime dateReceived;

    /** NULL until a Pharmacist approves the prescription. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approving_pharmacist_id")
    private Staff approvingPharmacist;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.Pending;

    @Column(name = "notes", length = 500)
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (dateReceived == null) {
            dateReceived = LocalDateTime.now();
        }
    }
}
