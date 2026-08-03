package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.request.DoctorRequest;
import com.app.pharmacy.domain.dtos.response.DoctorResponse;
import com.app.pharmacy.domain.entity.Doctor;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DoctorMapper {

    DoctorResponse toResponse(Doctor doctor);

    /** id is left unmapped (no matching source field) — @GeneratedValue handles it. */
    Doctor toEntity(DoctorRequest request);
}
