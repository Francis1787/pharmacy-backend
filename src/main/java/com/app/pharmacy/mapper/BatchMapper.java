package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.response.BatchResponse;
import com.app.pharmacy.domain.entity.Batch;
import com.app.pharmacy.mapper.support.RefSummaryMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = RefSummaryMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BatchMapper {

    @Mapping(target = "isExpired", expression = "java(batch.isExpired())")
    @Mapping(target = "purchaseOrderItemId",
            expression = "java(batch.getPurchaseOrderItem() != null ? batch.getPurchaseOrderItem().getId() : null)")
    @Mapping(target = "isControlledSubstance", source = "drug.isControlledSubstance")
    BatchResponse toResponse(Batch batch);
}
