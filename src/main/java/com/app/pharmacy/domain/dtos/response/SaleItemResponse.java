package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.dtos.common.RefSummary;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID id,
        RefSummary batch,
        RefSummary drug,
        int quantitySold,
        BigDecimal unitPriceAtSale
) {
}
