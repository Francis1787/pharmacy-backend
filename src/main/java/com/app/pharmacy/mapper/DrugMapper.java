package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.request.DrugCreateRequest;
import com.app.pharmacy.domain.dtos.response.DrugResponse;
import com.app.pharmacy.domain.entity.Drug;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DrugMapper {

    DrugResponse toResponse(Drug drug);

    /**
     * id is left unmapped (no matching source field) — @GeneratedValue handles it.
     * reorderThreshold falls back to 10 when the request omits it, since
     * DrugCreateRequest.reorderThreshold is nullable but Drug's isn't.
     */
    @Mapping(target = "reorderThreshold",
            expression = "java(request.reorderThreshold() != null ? request.reorderThreshold() : 10)")
    Drug toEntity(DrugCreateRequest request);
}
