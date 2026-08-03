package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.request.CustomerRequest;
import com.app.pharmacy.domain.dtos.response.CustomerResponse;
import com.app.pharmacy.domain.entity.Customer;
import com.app.pharmacy.exception.DuplicateResourceException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.CustomerRepository;
import com.app.pharmacy.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        customerRepository.findByPhoneNumber(request.phoneNumber()).ifPresent(existing -> {
            throw new DuplicateResourceException("A customer with phone number " + request.phoneNumber() + " already exists");
        });

        Customer customer = Customer.builder()
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .address(request.address())
                .build();

        return toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(UUID id, CustomerRequest request) {
        Customer customer = getCustomerOrThrow(id);

        // Only re-check uniqueness if the phone number is actually changing.
        if (!customer.getPhoneNumber().equals(request.phoneNumber())) {
            customerRepository.findByPhoneNumber(request.phoneNumber()).ifPresent(existing -> {
                throw new DuplicateResourceException("A customer with phone number " + request.phoneNumber() + " already exists");
            });
        }

        customer.setFullName(request.fullName());
        customer.setPhoneNumber(request.phoneNumber());
        customer.setAddress(request.address());

        return toResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponse getCustomerById(UUID id) {
        return toResponse(getCustomerOrThrow(id));
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<CustomerResponse> searchCustomersByName(String name) {
        return customerRepository.findByFullNameContainingIgnoreCase(name).stream().map(this::toResponse).toList();
    }

    private Customer getCustomerOrThrow(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getPhoneNumber(),
                customer.getAddress(),
                customer.getCreatedAt()
        );
    }
}
