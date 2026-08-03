package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.response.StaffResponse;
import com.app.pharmacy.domain.entity.Staff;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Deliberately never maps passwordHash — StaffResponse has no such field, so MapStruct simply can't touch it. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StaffMapper {

    StaffResponse toResponse(Staff staff);
}
