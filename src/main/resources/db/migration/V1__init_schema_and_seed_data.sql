-- =========================================================
-- Adom Community Pharmacy — Database Implementation Script
-- RDBMS: PostgreSQL 14+ (gen_random_uuid() is native since PG13, no extension needed)
-- Phase 7: DDL (schema creation) + DML (mock data population)
--
-- NOTE: PostgreSQL cannot CREATE DATABASE and USE it in one script.
-- First create the database from psql or a terminal:
--     createdb pharmacy_db
-- Then connect to it before running the rest of this script:
--     \c pharmacy_db
--
-- NOTE ON TABLE ORDER: PurchaseOrder and PurchaseOrderItem are created
-- BEFORE Batch, since Batch.purchase_order_item_id references PurchaseOrderItem.
--
-- NOTE ON UUID LITERALS IN MOCK DATA: since primary keys are no longer
-- small sequential integers, the DML below uses explicit, readable UUID
-- literals instead of relying on gen_random_uuid() at insert time — this
-- keeps foreign key references traceable by eye. The scheme is:
--     aaaaaaaa-TTTT-0000-0000-NNNNNNNNNNNN
-- where TTTT identifies the table (0001=Customer, 0002=Doctor, 0003=Staff,
-- 0004=Drug, 0005=Supplier, 0006=PurchaseOrder, 0007=PurchaseOrderItem,
-- 0008=Batch, 0009=Prescription, 000a=PrescriptionItem, 000b=Sale,
-- 000c=SaleItem, 000d=AuditLog) and NNNN...is the row number within
-- that table. In the real application, gen_random_uuid() generates
-- these automatically on every insert.
-- =========================================================

-- =========================================================
-- DDL: TABLE CREATION
-- Tables are created in dependency order (parents before children)
-- =========================================================

CREATE TABLE Customer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(150) NOT NULL,
    phone_number    VARCHAR(20)  NOT NULL UNIQUE,
    address         VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Doctor (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(150) NOT NULL,
    license_number  VARCHAR(50)  NOT NULL UNIQUE,
    contact_info    VARCHAR(150)
);

CREATE TABLE Staff (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(150) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    license_number  VARCHAR(50)  UNIQUE,
    phone_number    VARCHAR(20)  NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    must_reset_password BOOLEAN NOT NULL DEFAULT TRUE,
    hire_date       DATE NOT NULL,
    active_status   BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_staff_role CHECK (role IN ('Pharmacist','Technician','Admin'))
);

CREATE TABLE Drug (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(150) NOT NULL,
    generic_name             VARCHAR(150),
    dosage_form              VARCHAR(30) NOT NULL,
    strength                 VARCHAR(30) NOT NULL,
    unit_price               DECIMAL(10,2) NOT NULL,
    is_controlled_substance  BOOLEAN NOT NULL DEFAULT FALSE,
    reorder_threshold        INT NOT NULL DEFAULT 10,
    CONSTRAINT chk_drug_form CHECK (dosage_form IN ('Tablet','Syrup','Injection','Capsule','Cream','Other')),
    CONSTRAINT chk_drug_price CHECK (unit_price >= 0),
    CONSTRAINT chk_drug_threshold CHECK (reorder_threshold >= 0)
);

CREATE TABLE Supplier (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name    VARCHAR(150) NOT NULL UNIQUE,
    contact_person  VARCHAR(150),
    phone_number    VARCHAR(20) NOT NULL,
    email           VARCHAR(150),
    address         VARCHAR(255)
);

CREATE TABLE PurchaseOrder (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id               UUID NOT NULL,
    order_date                DATE NOT NULL DEFAULT CURRENT_DATE,
    expected_delivery_date    DATE,
    actual_delivery_date      DATE,
    status                    VARCHAR(20) NOT NULL DEFAULT 'Pending',
    created_by_staff_id       UUID NOT NULL,
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES Supplier(id),
    CONSTRAINT fk_po_staff FOREIGN KEY (created_by_staff_id) REFERENCES Staff(id),
    CONSTRAINT chk_po_status CHECK (status IN ('Pending','Received','Cancelled'))
);

CREATE TABLE PurchaseOrderItem (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id     UUID NOT NULL,
    drug_id               UUID NOT NULL,
    quantity_ordered      INT NOT NULL,
    unit_cost             DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_poi_order FOREIGN KEY (purchase_order_id) REFERENCES PurchaseOrder(id),
    CONSTRAINT fk_poi_drug FOREIGN KEY (drug_id) REFERENCES Drug(id),
    CONSTRAINT chk_poi_qty CHECK (quantity_ordered > 0),
    CONSTRAINT chk_poi_cost CHECK (unit_cost >= 0)
);

CREATE TABLE Batch (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drug_id                     UUID NOT NULL,
    batch_number                VARCHAR(50) NOT NULL,
    quantity_in_stock           INT NOT NULL DEFAULT 0,
    expiry_date                 DATE NOT NULL,
    date_received                DATE NOT NULL DEFAULT CURRENT_DATE,
    supplier_id                  UUID NOT NULL,
    purchase_order_item_id        UUID NULL,
    verified_by_pharmacist_id      UUID NULL,
    CONSTRAINT fk_batch_drug FOREIGN KEY (drug_id) REFERENCES Drug(id),
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id) REFERENCES Supplier(id),
    CONSTRAINT fk_batch_poi FOREIGN KEY (purchase_order_item_id) REFERENCES PurchaseOrderItem(id),
    CONSTRAINT fk_batch_verifier FOREIGN KEY (verified_by_pharmacist_id) REFERENCES Staff(id),
    CONSTRAINT chk_batch_qty CHECK (quantity_in_stock >= 0)
);

CREATE TABLE Prescription (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id               UUID NOT NULL,
    doctor_id                 UUID NOT NULL,
    date_issued               DATE NOT NULL,
    date_received             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approving_pharmacist_id   UUID NULL,
    approval_status           VARCHAR(20) NOT NULL DEFAULT 'Pending',
    notes                     VARCHAR(500),
    CONSTRAINT fk_prescription_customer FOREIGN KEY (customer_id) REFERENCES Customer(id),
    CONSTRAINT fk_prescription_doctor FOREIGN KEY (doctor_id) REFERENCES Doctor(id),
    CONSTRAINT fk_prescription_pharmacist FOREIGN KEY (approving_pharmacist_id) REFERENCES Staff(id),
    CONSTRAINT chk_prescription_status CHECK (approval_status IN ('Pending','Approved','Rejected'))
);

CREATE TABLE PrescriptionItem (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id         UUID NOT NULL,
    drug_id                 UUID NOT NULL,
    dosage_instructions     VARCHAR(255) NOT NULL,
    quantity_prescribed     INT NOT NULL,
    CONSTRAINT fk_pi_prescription FOREIGN KEY (prescription_id) REFERENCES Prescription(id),
    CONSTRAINT fk_pi_drug FOREIGN KEY (drug_id) REFERENCES Drug(id),
    CONSTRAINT chk_pi_qty CHECK (quantity_prescribed > 0)
);

CREATE TABLE Sale (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id             UUID NOT NULL UNIQUE,
    cashier_id                  UUID NOT NULL,
    dispensing_pharmacist_id    UUID NOT NULL,
    sale_date                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount                DECIMAL(10,2) NOT NULL,
    payment_method               VARCHAR(20) NOT NULL,
    CONSTRAINT fk_sale_prescription FOREIGN KEY (prescription_id) REFERENCES Prescription(id),
    CONSTRAINT fk_sale_cashier FOREIGN KEY (cashier_id) REFERENCES Staff(id),
    CONSTRAINT fk_sale_pharmacist FOREIGN KEY (dispensing_pharmacist_id) REFERENCES Staff(id),
    CONSTRAINT chk_sale_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_sale_payment CHECK (payment_method IN ('Cash','Mobile Money','Card'))
);

CREATE TABLE SaleItem (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id               UUID NOT NULL,
    batch_id              UUID NOT NULL,
    quantity_sold          INT NOT NULL,
    unit_price_at_sale     DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_si_sale FOREIGN KEY (sale_id) REFERENCES Sale(id),
    CONSTRAINT fk_si_batch FOREIGN KEY (batch_id) REFERENCES Batch(id),
    CONSTRAINT chk_si_qty CHECK (quantity_sold > 0),
    CONSTRAINT chk_si_price CHECK (unit_price_at_sale >= 0)
);

CREATE TABLE AuditLog (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id          UUID NOT NULL,
    action_type        VARCHAR(50) NOT NULL,
    reference_id        UUID NOT NULL,
    reference_table      VARCHAR(50) NOT NULL,
    "timestamp"           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes                  VARCHAR(500),
    CONSTRAINT fk_audit_staff FOREIGN KEY (staff_id) REFERENCES Staff(id),
    CONSTRAINT chk_audit_action CHECK (
        action_type IN ('Prescription Approved','Prescription Rejected','Drug Dispensed','Stock Updated','Purchase Order Created')
    )
);

-- =========================================================
-- DML: MOCK DATA POPULATION
-- =========================================================

-- Customers (table code 0001)
INSERT INTO Customer (id, full_name, phone_number, address) VALUES
('aaaaaaaa-0001-0000-0000-000000000001', 'Akosua Mensah', '0244123456', 'Adenta, Accra'),
('aaaaaaaa-0001-0000-0000-000000000002', 'Kwame Boateng', '0201987654', 'Osu, Accra'),
('aaaaaaaa-0001-0000-0000-000000000003', 'Efua Owusu', '0559871234', 'Madina, Accra'),
('aaaaaaaa-0001-0000-0000-000000000004', 'Yaw Asante', '0277654321', 'Dansoman, Accra'),
('aaaaaaaa-0001-0000-0000-000000000005', 'Abena Darko', '0501122334', 'East Legon, Accra');

-- Doctors (table code 0002)
INSERT INTO Doctor (id, full_name, license_number, contact_info) VALUES
('aaaaaaaa-0002-0000-0000-000000000001', 'Dr. Samuel Ofori', 'GMC-1023', '0302-556677'),
('aaaaaaaa-0002-0000-0000-000000000002', 'Dr. Linda Amoah', 'GMC-2044', '0302-889900'),
('aaaaaaaa-0002-0000-0000-000000000003', 'Dr. Peter Nartey', 'GMC-3067', '0302-112233');

-- Staff (table code 0003)
-- NOTE: password_hash values below are placeholder BCrypt hashes for demo purposes only.
-- In the real application, Spring Boot (using BCryptPasswordEncoder) generates these at
-- account-creation time — the database never receives or stores a plaintext password.
-- must_reset_password is FALSE for established accounts, TRUE for the new hire (row 6),
-- demonstrating the forced-reset-on-first-login flow.
INSERT INTO Staff (id, full_name, role, license_number, phone_number, email, password_hash, must_reset_password, hire_date, active_status) VALUES
('aaaaaaaa-0003-0000-0000-000000000001', 'Pharm. Grace Adjei', 'Pharmacist', 'PSGH-4521', '0244000111', 'grace.adjei@adompharmacy.com', '$2a$10$placeholderhash1234567890abcdefghijklmnopqrstuv', FALSE, '2021-04-01', TRUE),
('aaaaaaaa-0003-0000-0000-000000000002', 'Pharm. Michael Tetteh', 'Pharmacist', 'PSGH-4877', '0244000222', 'michael.tetteh@adompharmacy.com', '$2a$10$placeholderhash2345678901bcdefghijklmnopqrstuvw', FALSE, '2022-08-15', TRUE),
('aaaaaaaa-0003-0000-0000-000000000003', 'Comfort Appiah', 'Technician', NULL, '0244000333', 'comfort.appiah@adompharmacy.com', '$2a$10$placeholderhash3456789012cdefghijklmnopqrstuvwx', FALSE, '2023-01-10', TRUE),
('aaaaaaaa-0003-0000-0000-000000000004', 'Isaac Owusu', 'Technician', NULL, '0244000444', 'isaac.owusu@adompharmacy.com', '$2a$10$placeholderhash4567890123defghijklmnopqrstuvwxy', FALSE, '2023-06-01', TRUE),
('aaaaaaaa-0003-0000-0000-000000000005', 'Francis Danso', 'Admin', NULL, '0244000555', 'francis.danso@adompharmacy.com', '$2a$10$placeholderhash5678901234efghijklmnopqrstuvwxyz', FALSE, '2020-11-20', TRUE),
('aaaaaaaa-0003-0000-0000-000000000006', 'Pharm. Nana Yaa Boadi', 'Pharmacist', 'PSGH-5190', '0244000666', 'nanayaa.boadi@adompharmacy.com', '$2a$10$placeholderhashNEWHIRE6789012345fghijklmnopqrstuv', TRUE, '2026-07-19', TRUE);

-- Drugs (table code 0004)
INSERT INTO Drug (id, name, generic_name, dosage_form, strength, unit_price, is_controlled_substance, reorder_threshold) VALUES
('aaaaaaaa-0004-0000-0000-000000000001', 'Paracetamol', 'Acetaminophen', 'Tablet', '500mg', 0.15, FALSE, 200),
('aaaaaaaa-0004-0000-0000-000000000002', 'Amoxicillin', 'Amoxicillin', 'Capsule', '250mg', 0.35, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000003', 'Diazepam', 'Diazepam', 'Tablet', '5mg', 0.50, TRUE, 50),
('aaaaaaaa-0004-0000-0000-000000000004', 'Insulin Glargine', 'Insulin Glargine', 'Injection', '100units/ml', 45.00, FALSE, 20),
('aaaaaaaa-0004-0000-0000-000000000005', 'Amoxiclav Syrup', 'Amoxicillin/Clavulanate', 'Syrup', '125mg/5ml', 12.00, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000006', 'Tramadol', 'Tramadol HCl', 'Capsule', '50mg', 0.80, TRUE, 60),
('aaaaaaaa-0004-0000-0000-000000000007', 'Metformin', 'Metformin HCl', 'Tablet', '500mg', 0.20, FALSE, 200);

-- Suppliers (table code 0005)
INSERT INTO Supplier (id, company_name, contact_person, phone_number, email, address) VALUES
('aaaaaaaa-0005-0000-0000-000000000001', 'MedSupply Ghana Ltd', 'Kojo Ansah', '0302334455', 'sales@medsupplygh.com', 'Spintex Road, Accra'),
('aaaaaaaa-0005-0000-0000-000000000002', 'Accra Pharma Distributors', 'Ama Serwaa', '0302778899', 'info@accrapharma.com', 'Kaneshie, Accra');

-- Purchase Orders (table code 0006) — created before Batches, since batches reference order items
-- actual_delivery_date demonstrates delivery tracking: order 1 arrived on time, order 2 arrived 2 days late.
INSERT INTO PurchaseOrder (id, supplier_id, order_date, expected_delivery_date, actual_delivery_date, status, created_by_staff_id) VALUES
('aaaaaaaa-0006-0000-0000-000000000001', 'aaaaaaaa-0005-0000-0000-000000000001', '2026-07-01', '2026-07-10', '2026-07-10', 'Received', 'aaaaaaaa-0003-0000-0000-000000000005'),
('aaaaaaaa-0006-0000-0000-000000000002', 'aaaaaaaa-0005-0000-0000-000000000002', '2026-07-05', '2026-07-15', '2026-07-17', 'Received', 'aaaaaaaa-0003-0000-0000-000000000005');

-- Purchase Order Items (table code 0007)
-- Order 1 (Supplier 1): Paracetamol, Amoxicillin — non-controlled
-- Order 2 (Supplier 2): Diazepam, Tramadol — both controlled substances
INSERT INTO PurchaseOrderItem (id, purchase_order_id, drug_id, quantity_ordered, unit_cost) VALUES
('aaaaaaaa-0007-0000-0000-000000000001', 'aaaaaaaa-0006-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000001', 500, 0.10),
('aaaaaaaa-0007-0000-0000-000000000002', 'aaaaaaaa-0006-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000002', 300, 0.25),
('aaaaaaaa-0007-0000-0000-000000000003', 'aaaaaaaa-0006-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000003', 100, 0.35),
('aaaaaaaa-0007-0000-0000-000000000004', 'aaaaaaaa-0006-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000006', 120, 0.55);

-- Batches (table code 0008)
-- Batches 1, 2, 3, 6 trace back to a PurchaseOrderItem (delivery received against an order).
-- Batches 4, 5, 7 have no linked order (e.g. earlier/manual stock entries) — purchase_order_item_id is NULL.
-- Controlled-substance batches (3, 6) carry a verified_by_pharmacist_id per Business Rule 11;
-- non-controlled batches leave it NULL since verification isn't mandatory for them.
INSERT INTO Batch (id, drug_id, batch_number, quantity_in_stock, expiry_date, date_received, supplier_id, purchase_order_item_id, verified_by_pharmacist_id) VALUES
('aaaaaaaa-0008-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000001', 'PARA-2026-01', 500, '2027-12-31', '2026-01-15', 'aaaaaaaa-0005-0000-0000-000000000001', 'aaaaaaaa-0007-0000-0000-000000000001', NULL),
('aaaaaaaa-0008-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000002', 'AMOX-2026-02', 300, '2027-06-30', '2026-02-10', 'aaaaaaaa-0005-0000-0000-000000000001', 'aaaaaaaa-0007-0000-0000-000000000002', NULL),
('aaaaaaaa-0008-0000-0000-000000000003', 'aaaaaaaa-0004-0000-0000-000000000003', 'DIAZ-2026-01', 100, '2027-03-31', '2026-01-20', 'aaaaaaaa-0005-0000-0000-000000000002', 'aaaaaaaa-0007-0000-0000-000000000003', 'aaaaaaaa-0003-0000-0000-000000000001'),
('aaaaaaaa-0008-0000-0000-000000000004', 'aaaaaaaa-0004-0000-0000-000000000004', 'INSU-2026-01', 40, '2026-11-30', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000005', 'aaaaaaaa-0004-0000-0000-000000000005', 'AMSYR-2026-01', 60, '2027-01-31', '2026-02-15', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000006', 'aaaaaaaa-0004-0000-0000-000000000006', 'TRAM-2026-01', 120, '2027-05-31', '2026-01-25', 'aaaaaaaa-0005-0000-0000-000000000002', 'aaaaaaaa-0007-0000-0000-000000000004', 'aaaaaaaa-0003-0000-0000-000000000002'),
('aaaaaaaa-0008-0000-0000-000000000007', 'aaaaaaaa-0004-0000-0000-000000000007', 'METF-2026-01', 400, '2027-09-30', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL);

-- Prescriptions (table code 0009)
INSERT INTO Prescription (id, customer_id, doctor_id, date_issued, approving_pharmacist_id, approval_status, notes) VALUES
('aaaaaaaa-0009-0000-0000-000000000001', 'aaaaaaaa-0001-0000-0000-000000000001', 'aaaaaaaa-0002-0000-0000-000000000001', '2026-07-10', 'aaaaaaaa-0003-0000-0000-000000000001', 'Approved', 'No known allergies'),
('aaaaaaaa-0009-0000-0000-000000000002', 'aaaaaaaa-0001-0000-0000-000000000002', 'aaaaaaaa-0002-0000-0000-000000000002', '2026-07-12', 'aaaaaaaa-0003-0000-0000-000000000001', 'Approved', NULL),
('aaaaaaaa-0009-0000-0000-000000000003', 'aaaaaaaa-0001-0000-0000-000000000003', 'aaaaaaaa-0002-0000-0000-000000000001', '2026-07-14', 'aaaaaaaa-0003-0000-0000-000000000002', 'Approved', 'Follow-up required in 2 weeks'),
('aaaaaaaa-0009-0000-0000-000000000004', 'aaaaaaaa-0001-0000-0000-000000000004', 'aaaaaaaa-0002-0000-0000-000000000003', '2026-07-15', NULL, 'Pending', NULL),
('aaaaaaaa-0009-0000-0000-000000000005', 'aaaaaaaa-0001-0000-0000-000000000005', 'aaaaaaaa-0002-0000-0000-000000000002', '2026-07-16', 'aaaaaaaa-0003-0000-0000-000000000002', 'Approved', 'Diabetic patient');

-- Prescription Items (table code 000a)
INSERT INTO PrescriptionItem (id, prescription_id, drug_id, dosage_instructions, quantity_prescribed) VALUES
('aaaaaaaa-000a-0000-0000-000000000001', 'aaaaaaaa-0009-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000001', 'Take 1 tablet every 6 hours as needed', 20),
('aaaaaaaa-000a-0000-0000-000000000002', 'aaaaaaaa-0009-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000002', 'Take 1 capsule twice daily for 7 days', 14),
('aaaaaaaa-000a-0000-0000-000000000003', 'aaaaaaaa-0009-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000006', 'Take 1 capsule every 8 hours for pain', 15),
('aaaaaaaa-000a-0000-0000-000000000004', 'aaaaaaaa-0009-0000-0000-000000000003', 'aaaaaaaa-0004-0000-0000-000000000005', 'Take 5ml twice daily for 7 days', 1),
('aaaaaaaa-000a-0000-0000-000000000005', 'aaaaaaaa-0009-0000-0000-000000000004', 'aaaaaaaa-0004-0000-0000-000000000003', 'Take 1 tablet at night for anxiety', 10),
('aaaaaaaa-000a-0000-0000-000000000006', 'aaaaaaaa-0009-0000-0000-000000000005', 'aaaaaaaa-0004-0000-0000-000000000004', 'Inject 10 units once daily', 1),
('aaaaaaaa-000a-0000-0000-000000000007', 'aaaaaaaa-0009-0000-0000-000000000005', 'aaaaaaaa-0004-0000-0000-000000000007', 'Take 1 tablet twice daily', 60);

-- Sales (table code 000b) — only for approved & dispensed prescriptions
-- Per Rule 15, only a Pharmacist can complete a sale, so both cashier_id and dispensing_pharmacist_id
-- reference Pharmacist staff rows now (previously cashier_id could be a Technician).
-- Sale 1 and 3 show one Pharmacist handling both roles; Sale 2 and 4 show two Pharmacists splitting them.
INSERT INTO Sale (id, prescription_id, cashier_id, dispensing_pharmacist_id, total_amount, payment_method) VALUES
('aaaaaaaa-000b-0000-0000-000000000001', 'aaaaaaaa-0009-0000-0000-000000000001', 'aaaaaaaa-0003-0000-0000-000000000001', 'aaaaaaaa-0003-0000-0000-000000000001', 7.90, 'Mobile Money'),
('aaaaaaaa-000b-0000-0000-000000000002', 'aaaaaaaa-0009-0000-0000-000000000002', 'aaaaaaaa-0003-0000-0000-000000000002', 'aaaaaaaa-0003-0000-0000-000000000001', 12.00, 'Cash'),
('aaaaaaaa-000b-0000-0000-000000000003', 'aaaaaaaa-0009-0000-0000-000000000003', 'aaaaaaaa-0003-0000-0000-000000000002', 'aaaaaaaa-0003-0000-0000-000000000002', 12.00, 'Card'),
('aaaaaaaa-000b-0000-0000-000000000004', 'aaaaaaaa-0009-0000-0000-000000000005', 'aaaaaaaa-0003-0000-0000-000000000001', 'aaaaaaaa-0003-0000-0000-000000000002', 57.00, 'Mobile Money');

-- Sale Items (table code 000c)
INSERT INTO SaleItem (id, sale_id, batch_id, quantity_sold, unit_price_at_sale) VALUES
('aaaaaaaa-000c-0000-0000-000000000001', 'aaaaaaaa-000b-0000-0000-000000000001', 'aaaaaaaa-0008-0000-0000-000000000001', 20, 0.15),
('aaaaaaaa-000c-0000-0000-000000000002', 'aaaaaaaa-000b-0000-0000-000000000001', 'aaaaaaaa-0008-0000-0000-000000000002', 14, 0.35),
('aaaaaaaa-000c-0000-0000-000000000003', 'aaaaaaaa-000b-0000-0000-000000000002', 'aaaaaaaa-0008-0000-0000-000000000006', 15, 0.80),
('aaaaaaaa-000c-0000-0000-000000000004', 'aaaaaaaa-000b-0000-0000-000000000003', 'aaaaaaaa-0008-0000-0000-000000000005', 1, 12.00),
('aaaaaaaa-000c-0000-0000-000000000005', 'aaaaaaaa-000b-0000-0000-000000000004', 'aaaaaaaa-0008-0000-0000-000000000004', 1, 45.00),
('aaaaaaaa-000c-0000-0000-000000000006', 'aaaaaaaa-000b-0000-0000-000000000004', 'aaaaaaaa-0008-0000-0000-000000000007', 60, 0.20);

-- Audit Log (table code 000d)
INSERT INTO AuditLog (id, staff_id, action_type, reference_id, reference_table, notes) VALUES
('aaaaaaaa-000d-0000-0000-000000000001', 'aaaaaaaa-0003-0000-0000-000000000001', 'Prescription Approved', 'aaaaaaaa-0009-0000-0000-000000000001', 'Prescription', 'Reviewed for drug interactions - none found'),
('aaaaaaaa-000d-0000-0000-000000000002', 'aaaaaaaa-0003-0000-0000-000000000001', 'Drug Dispensed', 'aaaaaaaa-000b-0000-0000-000000000001', 'Sale', 'Dispensed Paracetamol and Amoxicillin'),
('aaaaaaaa-000d-0000-0000-000000000003', 'aaaaaaaa-0003-0000-0000-000000000002', 'Prescription Approved', 'aaaaaaaa-0009-0000-0000-000000000003', 'Prescription', NULL),
('aaaaaaaa-000d-0000-0000-000000000004', 'aaaaaaaa-0003-0000-0000-000000000002', 'Drug Dispensed', 'aaaaaaaa-000b-0000-0000-000000000004', 'Sale', 'Dispensed Insulin and Metformin'),
('aaaaaaaa-000d-0000-0000-000000000005', 'aaaaaaaa-0003-0000-0000-000000000005', 'Purchase Order Created', 'aaaaaaaa-0006-0000-0000-000000000001', 'PurchaseOrder', 'Restocking Paracetamol and Amoxicillin'),
('aaaaaaaa-000d-0000-0000-000000000006', 'aaaaaaaa-0003-0000-0000-000000000001', 'Stock Updated', 'aaaaaaaa-0008-0000-0000-000000000003', 'Batch', 'Verified Diazepam delivery matches purchase order — controlled substance'),
('aaaaaaaa-000d-0000-0000-000000000007', 'aaaaaaaa-0003-0000-0000-000000000002', 'Stock Updated', 'aaaaaaaa-0008-0000-0000-000000000006', 'Batch', 'Verified Tramadol delivery matches purchase order — controlled substance');
