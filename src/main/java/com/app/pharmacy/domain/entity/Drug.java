package com.app.pharmacy.domain.entity;

import com.app.pharmacy.domain.entity.enums.DosageForm;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Master drug catalog entry.
 *
 * unit_price and is_controlled_substance are edit-restricted to Admin
 * only (Rule 12) — that restriction is enforced at the service/controller
 * layer via @PreAuthorize, not by anything in this entity.
 */
@Entity
@Table(name = "drug")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "generic_name", length = 150)
    private String genericName;

    @Enumerated(EnumType.STRING)
    @Column(name = "dosage_form", nullable = false, length = 30)
    private DosageForm dosageForm;

    @Column(name = "strength", nullable = false, length = 30)
    private String strength;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "is_controlled_substance", nullable = false)
    @Builder.Default
    private Boolean isControlledSubstance = false;

    @Column(name = "reorder_threshold", nullable = false)
    @Builder.Default
    private Integer reorderThreshold = 10;
}
