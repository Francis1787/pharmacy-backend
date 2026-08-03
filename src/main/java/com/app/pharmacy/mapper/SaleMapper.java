package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.response.SaleItemResponse;
import com.app.pharmacy.domain.dtos.response.SaleResponse;
import com.app.pharmacy.domain.entity.Prescription;
import com.app.pharmacy.domain.entity.Sale;
import com.app.pharmacy.domain.entity.SaleItem;
import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import com.app.pharmacy.mapper.support.RefSummaryMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * items is a separate parameter — see PurchaseOrderMapper's javadoc for why.
 *
 * Two things need help here that the other mappers don't:
 *  - Sale.paymentMethod is stored as a raw DB String ("Mobile Money"), but
 *    SaleResponse.paymentMethod is the PaymentMethod enum — the local
 *    default method below bridges that via PaymentMethod.fromDbValue().
 *  - Prescription has no single display name field, so its RefSummary
 *    label falls back to the prescription's own ID — specific enough to
 *    this one use that it stays local rather than joining RefSummaryMapper.
 */
@Mapper(componentModel = "spring", uses = RefSummaryMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SaleMapper {

    SaleResponse toResponse(Sale sale, List<SaleItem> items);

    /** SaleItem has no direct "drug" property — it's reached via batch.drug. */
    @Mapping(target = "drug", source = "batch.drug")
    SaleItemResponse itemToResponse(SaleItem item);

    default RefSummary map(Prescription prescription) {
        return new RefSummary(prescription.getId(), prescription.getId().toString());
    }

    default PaymentMethod map(String paymentMethod) {
        return PaymentMethod.fromDbValue(paymentMethod);
    }
}
