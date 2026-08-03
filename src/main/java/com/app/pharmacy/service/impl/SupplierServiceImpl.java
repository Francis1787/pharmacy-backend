package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.request.SupplierRequest;
import com.app.pharmacy.domain.dtos.response.SupplierResponse;
import com.app.pharmacy.domain.entity.Supplier;
import com.app.pharmacy.exception.DuplicateResourceException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.SupplierRepository;
import com.app.pharmacy.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    @Override
    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        supplierRepository.findByCompanyName(request.companyName()).ifPresent(existing -> {
            throw new DuplicateResourceException("A supplier named " + request.companyName() + " already exists");
        });

        Supplier supplier = Supplier.builder()
                .companyName(request.companyName())
                .contactPerson(request.contactPerson())
                .phoneNumber(request.phoneNumber())
                .email(request.email())
                .address(request.address())
                .build();

        return toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(UUID id, SupplierRequest request) {
        Supplier supplier = getSupplierOrThrow(id);

        if (!supplier.getCompanyName().equals(request.companyName())) {
            supplierRepository.findByCompanyName(request.companyName()).ifPresent(existing -> {
                throw new DuplicateResourceException("A supplier named " + request.companyName() + " already exists");
            });
        }

        supplier.setCompanyName(request.companyName());
        supplier.setContactPerson(request.contactPerson());
        supplier.setPhoneNumber(request.phoneNumber());
        supplier.setEmail(request.email());
        supplier.setAddress(request.address());

        return toResponse(supplierRepository.save(supplier));
    }

    @Override
    public SupplierResponse getSupplierById(UUID id) {
        return toResponse(getSupplierOrThrow(id));
    }

    @Override
    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepository.findAll().stream().map(this::toResponse).toList();
    }

    private Supplier getSupplierOrThrow(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getCompanyName(),
                supplier.getContactPerson(),
                supplier.getPhoneNumber(),
                supplier.getEmail(),
                supplier.getAddress()
        );
    }
}
