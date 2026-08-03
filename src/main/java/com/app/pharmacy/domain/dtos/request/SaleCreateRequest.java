package com.app.pharmacy.domain.dtos.request;

import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Pharmacist only (Rule 15). totalAmount and each item's unitPriceAtSale
 * are computed server-side from Drug.unit_price at the moment of sale —
 * never accepted from the client, so a request can't under/overstate
 * what's owed. The service layer also enforces Rules 1, 4, 5 here:
 * rejects if the prescription isn't Approved, decrements batch stock
 * automatically, and blocks any batch that's expired.
 *
 * dispensingPharmacistId defaults to the authenticated caller if omitted;
 * cashierId likewise, unless a second Pharmacist is handling checkout
 * separately from dispensing (Rule 15 allows either split).
 */
public record SaleCreateRequest(
        @NotNull UUID prescriptionId,
        @NotNull PaymentMethod paymentMethod,

        UUID cashierId,
        UUID dispensingPharmacistId,

        @NotEmpty @Valid
        List<Item> items
) {
    public record Item(
            @NotNull UUID batchId,
            @Min(1) int quantitySold
    ) {
    }
}
