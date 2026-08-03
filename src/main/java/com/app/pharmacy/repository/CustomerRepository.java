package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByPhoneNumber(String phoneNumber);

    /** Simple name search for walk-in identification (Rule 9). */
    java.util.List<Customer> findByFullNameContainingIgnoreCase(String fullName);
}
