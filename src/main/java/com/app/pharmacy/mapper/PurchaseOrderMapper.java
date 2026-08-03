package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.response.PurchaseOrderItemResponse;
import com.app.pharmacy.domain.dtos.response.PurchaseOrderResponse;
import com.app.pharmacy.domain.entity.PurchaseOrder;
import com.app.pharmacy.domain.entity.PurchaseOrderItem;
import com.app.pharmacy.mapper.support.RefSummaryMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * toResponse takes items as a separate parameter rather than reading
 * order.getItems() — there's no such collection on the entity by design
 * (see PurchaseOrderItemRepository); the service layer fetches items
 * itself and passes them in here. MapStruct resolves target property
 * "items" against this second parameter automatically since no other
 * parameter has a same-named property.
 */
@Mapper(componentModel = "spring", uses = RefSummaryMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PurchaseOrderMapper {

    @Mapping(target = "isOverdue", expression = "java("
            + "order.getActualDeliveryDate() == null "
            + "&& order.getExpectedDeliveryDate() != null "
            + "&& order.getExpectedDeliveryDate().isBefore(java.time.LocalDate.now()) "
            + "&& order.getStatus() != com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus.Cancelled)")
    PurchaseOrderResponse toResponse(PurchaseOrder order, List<PurchaseOrderItem> items);

    PurchaseOrderItemResponse itemToResponse(PurchaseOrderItem item);
}
