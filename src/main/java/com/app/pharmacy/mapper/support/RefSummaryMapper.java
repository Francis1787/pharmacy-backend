package com.app.pharmacy.mapper.support;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.entity.Batch;
import com.app.pharmacy.domain.entity.Drug;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.Supplier;

/**
 * Static entity -> RefSummary conversions, referenced by multiple MapStruct
 * mappers via @Mapper(uses = RefSummaryMapper.class). Centralized here so
 * AuditLogMapper, BatchMapper, PurchaseOrderMapper, PrescriptionMapper, and
 * SaleMapper don't each redeclare the same Staff/Drug/Supplier/Batch ->
 * RefSummary logic.
 *
 * Plain static methods (not a Spring bean, not itself a @Mapper) — MapStruct
 * calls these directly at compile time without needing DI, so there's no
 * bean-wiring failure mode here regardless of component scanning.
 *
 * Customer and Doctor -> RefSummary aren't needed by any mapper besides
 * PrescriptionMapper, so those two stay local to PrescriptionMapper as
 * default methods rather than being added here for a single caller.
 */
public final class RefSummaryMapper {

    private RefSummaryMapper() {
    }

    public static RefSummary from(Staff staff) {
        return new RefSummary(staff.getId(), staff.getFullName());
    }

    public static RefSummary from(Drug drug) {
        return new RefSummary(drug.getId(), drug.getName());
    }

    public static RefSummary from(Supplier supplier) {
        return new RefSummary(supplier.getId(), supplier.getCompanyName());
    }

    public static RefSummary from(Batch batch) {
        return new RefSummary(batch.getId(), batch.getBatchNumber());
    }
}
