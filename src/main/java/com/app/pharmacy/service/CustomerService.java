package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.CustomerRequest;
import com.app.pharmacy.domain.dtos.response.CustomerResponse;

import java.util.List;
import java.util.UUID;

/**
 * Create and edit are Pharmacist-only (Rule 14) — enforced via @PreAuthorize
 * at the controller layer, not here. This interface stays role-agnostic so
 * it's testable independent of the security layer.
 */
public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse updateCustomer(UUID id, CustomerRequest request);

    CustomerResponse getCustomerById(UUID id);

    List<CustomerResponse> getAllCustomers();

    List<CustomerResponse> searchCustomersByName(String name);
}
