package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.response.AuditLogResponse;
import com.app.pharmacy.domain.entity.AuditLog;
import com.app.pharmacy.mapper.support.RefSummaryMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = RefSummaryMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog log);
}
