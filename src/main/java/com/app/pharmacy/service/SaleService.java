package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.SaleCreateRequest;
import com.app.pharmacy.domain.dtos.response.SaleResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SaleService {

    /**
     * Pharmacist only (Rule 15). authenticatedStaffId is used as the default
     * for cashierId/dispensingPharmacistId when the request omits them.
     *
     * Enforces, in order:
     *  - Rule 1: prescription must exist and be Approved; not already sold (Sale.prescription_id UNIQUE)
     *  - Rule 5: every batch drawn from must not be expired
     *  - Rule 4: stock is decremented automatically, never manually
     *  - Rule 3: controlled-substance line items get an extra-detail audit note
     * total_amount and unit_price_at_sale are computed server-side, never client-supplied.
     */
    SaleResponse createSale(SaleCreateRequest request, UUID authenticatedStaffId);

    SaleResponse getSaleById(UUID id);

    List<SaleResponse> getAllSales();

    List<SaleResponse> getSalesByDateRange(LocalDateTime from, LocalDateTime to);

    List<SaleResponse> getSalesByDispensingPharmacist(UUID pharmacistId);
}
