package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Prescription;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByCustomerId(UUID customerId);

    List<Prescription> findByApprovalStatus(ApprovalStatus status);

    List<Prescription> findByCustomerIdAndApprovalStatus(UUID customerId, ApprovalStatus status);
}
