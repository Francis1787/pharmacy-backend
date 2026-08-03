package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pharmacist or Technician — intake logging. Line items are created as
 * part of this request; PrescriptionItem has no standalone POST endpoint.
 * approvalStatus always starts Pending regardless of who submits this —
 * only PATCH /prescriptions/{id}/approve (Pharmacist only, Rule 2) can move it forward.
 */
public record PrescriptionCreateRequest(
        @NotNull UUID customerId,
        @NotNull UUID doctorId,

        @NotNull @PastOrPresent(message = "dateIssued cannot be in the future")
        LocalDate dateIssued,

        String notes,

        @NotEmpty @Valid
        List<Item> items
) {
    public record Item(
            @NotNull UUID drugId,
            @NotBlank String dosageInstructions,
            @Min(1) int quantityPrescribed
    ) {
    }
}
