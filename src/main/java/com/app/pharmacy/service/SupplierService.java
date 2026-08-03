package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.SupplierRequest;
import com.app.pharmacy.domain.dtos.response.SupplierResponse;

import java.util.List;
import java.util.UUID;

/** Admin only (create and edit, Rule 16). */
public interface SupplierService {

    SupplierResponse createSupplier(SupplierRequest request);

    SupplierResponse updateSupplier(UUID id, SupplierRequest request);

    SupplierResponse getSupplierById(UUID id);

    List<SupplierResponse> getAllSuppliers();
}
