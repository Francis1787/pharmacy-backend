package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.DrugControlledStatusUpdateRequest;
import com.app.pharmacy.domain.dtos.request.DrugCorrectionRequest;
import com.app.pharmacy.domain.dtos.request.DrugCreateRequest;
import com.app.pharmacy.domain.dtos.request.DrugPriceUpdateRequest;
import com.app.pharmacy.domain.dtos.response.DrugResponse;

import java.util.List;
import java.util.UUID;

public interface DrugService {

    /** Pharmacist or Technician. */
    DrugResponse createDrug(DrugCreateRequest request);

    /** Pharmacist or Technician — name/generic_name only. */
    DrugResponse correctDrug(UUID id, DrugCorrectionRequest request);

    /** Admin only (Rule 12). */
    DrugResponse updatePrice(UUID id, DrugPriceUpdateRequest request);

    /** Admin only (Rule 12). */
    DrugResponse updateControlledStatus(UUID id, DrugControlledStatusUpdateRequest request);

    DrugResponse getDrugById(UUID id);

    List<DrugResponse> getAllDrugs();

    List<DrugResponse> searchDrugsByName(String name);

    /** Rule 8 — drugs whose total non-expired batch stock has fallen to/below their reorder threshold. */
    List<DrugResponse> getDrugsBelowReorderThreshold();
}
