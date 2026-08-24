package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.CustomerRequest;
import com.app.pharmacy.domain.dtos.request.DoctorRequest;
import com.app.pharmacy.domain.dtos.request.SupplierRequest;
import com.app.pharmacy.domain.dtos.response.CustomerResponse;
import com.app.pharmacy.domain.dtos.response.DoctorResponse;
import com.app.pharmacy.domain.dtos.response.SupplierResponse;
import com.app.pharmacy.domain.entity.Customer;
import com.app.pharmacy.domain.entity.Doctor;
import com.app.pharmacy.domain.entity.Supplier;
import com.app.pharmacy.exception.DuplicateResourceException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.CustomerRepository;
import com.app.pharmacy.repository.DoctorRepository;
import com.app.pharmacy.repository.SupplierRepository;
import com.app.pharmacy.service.impl.CustomerServiceImpl;
import com.app.pharmacy.service.impl.DoctorServiceImpl;
import com.app.pharmacy.service.impl.SupplierServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Customer, Doctor and Supplier are the three reference-data services. They
 * share an identical shape — create, update, lookup — and one shared rule:
 * a natural key must stay unique, but re-saving a record without changing
 * that key must not trip the uniqueness check against the record itself.
 *
 * That self-collision case is the bug this trio invites, so each service is
 * tested for it explicitly. They are grouped in one class because testing
 * them separately would triple the boilerplate to say the same three things.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Reference data — Customer, Doctor, Supplier")
class DirectoryServicesTest {

    @Nested
    @DisplayName("CustomerService — natural key is phone number")
    class Customers {

        @Mock private CustomerRepository customerRepository;
        @InjectMocks private CustomerServiceImpl customerService;

        private Customer existing;

        @BeforeEach
        void setUp() {
            existing = TestFixtures.customer();
            when(customerRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
            when(customerRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
                Customer c = inv.getArgument(0);
                if (c.getId() == null) {
                    c.setId(UUID.randomUUID());
                }
                return c;
            });
        }

        private CustomerRequest request(String name, String phone) {
            return new CustomerRequest(name, phone, "5 Oxford Street, Osu");
        }

        @Test
        @DisplayName("creates a customer and echoes back the stored values")
        void createsCustomer() {
            CustomerResponse response = customerService.createCustomer(request("Efua Sarpong", "+233240000009"));

            assertThat(response.fullName()).isEqualTo("Efua Sarpong");
            assertThat(response.phoneNumber()).isEqualTo("+233240000009");
        }

        @Test
        @DisplayName("409s when the phone number already belongs to someone")
        void rejectsDuplicatePhone() {
            when(customerRepository.findByPhoneNumber("+233240000001")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> customerService.createCustomer(request("Someone Else", "+233240000001")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("phone number");

            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows an update that keeps the customer's own phone number")
        void updateKeepingOwnPhoneNumber() {
            when(customerRepository.findByPhoneNumber(existing.getPhoneNumber())).thenReturn(Optional.of(existing));

            CustomerResponse response = customerService.updateCustomer(
                    existing.getId(), request("Efua Sarpong-Mensah", existing.getPhoneNumber()));

            assertThat(response.fullName()).isEqualTo("Efua Sarpong-Mensah");
        }

        @Test
        @DisplayName("409s when an update claims another customer's phone number")
        void updateRejectsForeignPhoneNumber() {
            Customer other = TestFixtures.customer();
            when(customerRepository.findByPhoneNumber("+233240009999")).thenReturn(Optional.of(other));

            assertThatThrownBy(() -> customerService.updateCustomer(
                    existing.getId(), request("Efua Sarpong", "+233240009999")))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("404s on an unknown customer")
        void unknownCustomer() {
            UUID unknown = UUID.randomUUID();
            when(customerRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getCustomerById(unknown))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found");
        }

        @Test
        @DisplayName("delegates name search to a case-insensitive query")
        void searchesByName() {
            when(customerRepository.findByFullNameContainingIgnoreCase("efua")).thenReturn(List.of(existing));

            assertThat(customerService.searchCustomersByName("efua")).hasSize(1);
            verify(customerRepository).findByFullNameContainingIgnoreCase("efua");
        }
    }

    @Nested
    @DisplayName("DoctorService — natural key is licence number")
    class Doctors {

        @Mock private DoctorRepository doctorRepository;
        @InjectMocks private DoctorServiceImpl doctorService;

        private Doctor existing;

        @BeforeEach
        void setUp() {
            existing = TestFixtures.doctor();
            when(doctorRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
            when(doctorRepository.findByLicenseNumber(any())).thenReturn(Optional.empty());
            when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
                Doctor d = inv.getArgument(0);
                if (d.getId() == null) {
                    d.setId(UUID.randomUUID());
                }
                return d;
            });
        }

        private DoctorRequest request(String name, String licence) {
            return new DoctorRequest(name, licence, "+233270000001");
        }

        @Test
        @DisplayName("creates a prescriber and stores the licence number")
        void createsDoctor() {
            DoctorResponse response = doctorService.createDoctor(request("Dr. Nana Adjei", "MD-9001"));

            assertThat(response.licenseNumber()).isEqualTo("MD-9001");
            assertThat(response.fullName()).isEqualTo("Dr. Nana Adjei");
        }

        @Test
        @DisplayName("409s on a duplicate licence number — two prescribers cannot share one")
        void rejectsDuplicateLicence() {
            when(doctorRepository.findByLicenseNumber("MD-4471")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> doctorService.createDoctor(request("Dr. Impostor", "MD-4471")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("license number");

            verify(doctorRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows an update that keeps the doctor's own licence number")
        void updateKeepingOwnLicence() {
            when(doctorRepository.findByLicenseNumber(existing.getLicenseNumber())).thenReturn(Optional.of(existing));

            DoctorResponse response = doctorService.updateDoctor(
                    existing.getId(), request("Dr. Kwame Asante Jr.", existing.getLicenseNumber()));

            assertThat(response.fullName()).isEqualTo("Dr. Kwame Asante Jr.");
        }

        @Test
        @DisplayName("409s when an update claims another doctor's licence number")
        void updateRejectsForeignLicence() {
            when(doctorRepository.findByLicenseNumber("MD-8888")).thenReturn(Optional.of(TestFixtures.doctor()));

            assertThatThrownBy(() -> doctorService.updateDoctor(existing.getId(), request("Dr. X", "MD-8888")))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("404s on an unknown doctor")
        void unknownDoctor() {
            UUID unknown = UUID.randomUUID();
            when(doctorRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> doctorService.getDoctorById(unknown))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Doctor not found");
        }
    }

    @Nested
    @DisplayName("SupplierService — natural key is company name")
    class Suppliers {

        @Mock private SupplierRepository supplierRepository;
        @InjectMocks private SupplierServiceImpl supplierService;

        private Supplier existing;

        @BeforeEach
        void setUp() {
            existing = TestFixtures.supplier();
            when(supplierRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
            when(supplierRepository.findByCompanyName(any())).thenReturn(Optional.empty());
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> {
                Supplier s = inv.getArgument(0);
                if (s.getId() == null) {
                    s.setId(UUID.randomUUID());
                }
                return s;
            });
        }

        private SupplierRequest request(String company) {
            return new SupplierRequest(company, "Nii Armah", "+233300000001", "sales@accramed.test", "12 Ring Road");
        }

        @Test
        @DisplayName("creates a supplier with its full contact details")
        void createsSupplier() {
            SupplierResponse response = supplierService.createSupplier(request("Tema Pharma Distributors"));

            assertThat(response.companyName()).isEqualTo("Tema Pharma Distributors");
            assertThat(response.contactPerson()).isEqualTo("Nii Armah");
            assertThat(response.email()).isEqualTo("sales@accramed.test");
        }

        @Test
        @DisplayName("409s on a duplicate company name")
        void rejectsDuplicateCompanyName() {
            when(supplierRepository.findByCompanyName("Accra Medical Supplies")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> supplierService.createSupplier(request("Accra Medical Supplies")))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(supplierRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows an update that keeps the supplier's own company name")
        void updateKeepingOwnName() {
            when(supplierRepository.findByCompanyName(existing.getCompanyName())).thenReturn(Optional.of(existing));

            SupplierResponse response = supplierService.updateSupplier(
                    existing.getId(), new SupplierRequest(existing.getCompanyName(), "New Contact",
                            "+233300000002", "new@accramed.test", "New address"));

            assertThat(response.contactPerson()).isEqualTo("New Contact");
            assertThat(response.companyName()).isEqualTo(existing.getCompanyName());
        }

        @Test
        @DisplayName("409s when an update claims another supplier's company name")
        void updateRejectsForeignName() {
            when(supplierRepository.findByCompanyName("Rival Supplies")).thenReturn(Optional.of(TestFixtures.supplier()));

            assertThatThrownBy(() -> supplierService.updateSupplier(existing.getId(), request("Rival Supplies")))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("404s on an unknown supplier")
        void unknownSupplier() {
            UUID unknown = UUID.randomUUID();
            when(supplierRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> supplierService.getSupplierById(unknown))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Supplier not found");
        }

        @Test
        @DisplayName("lists every supplier")
        void listsAll() {
            when(supplierRepository.findAll()).thenReturn(List.of(existing, TestFixtures.supplier()));

            assertThat(supplierService.getAllSuppliers()).hasSize(2);
        }
    }
}
