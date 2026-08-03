package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.request.SupplierRequest;
import com.app.pharmacy.domain.dtos.response.SupplierResponse;
import com.app.pharmacy.domain.entity.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SupplierMapper {

    SupplierResponse toResponse(Supplier supplier);

    /** id is left unmapped (no matching source field) — @GeneratedValue handles it. */
    Supplier toEntity(SupplierRequest request);
}
