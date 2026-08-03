package com.app.pharmacy.domain.dtos.request;

import com.app.pharmacy.domain.entity.enums.DosageForm;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Pharmacist or Technician (per API overview). */
public record DrugCreateRequest(
        @NotBlank String name,
        String genericName,
        @NotNull DosageForm dosageForm,
        @NotBlank String strength,

        @NotNull @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal unitPrice,

        boolean isControlledSubstance,

        /** Nullable — null means "use the DB default of 10" (see Drug.reorder_threshold DDL). */
        @Min(0) Integer reorderThreshold
) {
}
