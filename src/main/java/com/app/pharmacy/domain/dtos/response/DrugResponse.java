package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.entity.enums.DosageForm;

import java.math.BigDecimal;
import java.util.UUID;

public record DrugResponse(
        UUID id,
        String name,
        String genericName,
        DosageForm dosageForm,
        String strength,
        BigDecimal unitPrice,
        boolean isControlledSubstance,
        int reorderThreshold
) {
}
