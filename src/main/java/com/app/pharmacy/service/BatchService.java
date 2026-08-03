package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.BatchCreateRequest;
import com.app.pharmacy.domain.dtos.response.BatchResponse;

import java.util.List;
import java.util.UUID;

public interface BatchService {

    /** Pharmacist or Technician. Controlled-substance batches still need verify() before being sellable. */
    BatchResponse createBatch(BatchCreateRequest request);

    /**
     * Pharmacist only (Rule 11). Confirms the delivered batch matches what
     * was ordered; sets verified_by_pharmacist_id. pharmacistId is the
     * authenticated caller, not client-supplied.
     */
    BatchResponse verifyBatch(UUID batchId, UUID pharmacistId);

    BatchResponse getBatchById(UUID id);

    List<BatchResponse> getAllBatches();

    List<BatchResponse> getBatchesByDrug(UUID drugId);

    List<BatchResponse> getBatchesExpiringWithinDays(int days);
}
