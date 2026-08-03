package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.request.CustomerRequest;
import com.app.pharmacy.domain.dtos.response.CustomerResponse;
import com.app.pharmacy.domain.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);

    /** id and createdAt are left unmapped (no matching source field) — generation/@PrePersist handles both. */
    Customer toEntity(CustomerRequest request);
}
