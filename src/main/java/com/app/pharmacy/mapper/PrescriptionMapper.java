package com.app.pharmacy.mapper;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.response.PrescriptionItemResponse;
import com.app.pharmacy.domain.dtos.response.PrescriptionResponse;
import com.app.pharmacy.domain.entity.Customer;
import com.app.pharmacy.domain.entity.Doctor;
import com.app.pharmacy.domain.entity.Prescription;
import com.app.pharmacy.domain.entity.PrescriptionItem;
import com.app.pharmacy.mapper.support.RefSummaryMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * items is a separate parameter, not an entity collection — see
 * PurchaseOrderMapper's javadoc for why. Customer/Doctor -> RefSummary
 * stay local as default methods here since no other mapper needs them.
 */
@Mapper(componentModel = "spring", uses = RefSummaryMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PrescriptionMapper {

    PrescriptionResponse toResponse(Prescription prescription, List<PrescriptionItem> items);

    PrescriptionItemResponse itemToResponse(PrescriptionItem item);

    default RefSummary map(Customer customer) {
        return new RefSummary(customer.getId(), customer.getFullName());
    }

    default RefSummary map(Doctor doctor) {
        return new RefSummary(doctor.getId(), doctor.getFullName());
    }
}
