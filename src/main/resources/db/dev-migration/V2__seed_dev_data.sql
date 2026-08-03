-- =========================================================
-- Adom Community Pharmacy — Dev/Demo Seed Data
-- DEV/TEST ONLY. Do not apply this migration in production.
--
-- Populates all 13 tables with representative mock data:
-- 5 customers, 3 doctors, 6 staff (3 pharmacists, 2 technicians,
-- 1 admin, 1 new-hire pharmacist mid-password-reset), 62 drugs
-- (8 controlled substances), 2 suppliers, 2 purchase orders (6 line items),
-- 62 batches, 5 prescriptions, 4 completed sales, and audit log entries.
-- Note: a separate permanent admin account (admin@adompharmacy.com) is
-- seeded in V3__seed_admin_user.sql, which runs in every environment,
-- not just dev — so it is NOT duplicated here.
--
-- password_hash values are placeholder BCrypt hashes — Spring Boot
-- (BCryptPasswordEncoder) generates real hashes at account creation;
-- the database never receives or stores a plaintext password.
-- =========================================================

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
('aaaaaaaa-0003-0000-0000-000000000001', 'Pharm. Grace Adjei', 'Pharmacist', 'PSGH-4521', '0244000111', 'grace.adjei@adompharmacy.com', '$2b$10$CMbm7vNg/GHEK3QidZyVNeuws/EpWeBE3nPl9I.8bJq2mCDCWISIW', FALSE, '2021-04-01', TRUE),
('aaaaaaaa-0003-0000-0000-000000000002', 'Pharm. Michael Tetteh', 'Pharmacist', 'PSGH-4877', '0244000222', 'michael.tetteh@adompharmacy.com', '$2b$10$CMbm7vNg/GHEK3QidZyVNeuws/EpWeBE3nPl9I.8bJq2mCDCWISIW', FALSE, '2022-08-15', TRUE),
('aaaaaaaa-0003-0000-0000-000000000003', 'Comfort Appiah', 'Technician', NULL, '0244000333', 'comfort.appiah@adompharmacy.com', '$2b$10$CMbm7vNg/GHEK3QidZyVNeuws/EpWeBE3nPl9I.8bJq2mCDCWISIW', FALSE, '2023-01-10', TRUE),
('aaaaaaaa-0003-0000-0000-000000000004', 'Isaac Owusu', 'Technician', NULL, '0244000444', 'isaac.owusu@adompharmacy.com', '$2b$10$CMbm7vNg/GHEK3QidZyVNeuws/EpWeBE3nPl9I.8bJq2mCDCWISIW', FALSE, '2023-06-01', TRUE),
('aaaaaaaa-0003-0000-0000-000000000005', 'Francis Danso', 'Admin', NULL, '0244000555', 'francis.danso@adompharmacy.com', '$2b$10$CMbm7vNg/GHEK3QidZyVNeuws/EpWeBE3nPl9I.8bJq2mCDCWISIW', FALSE, '2020-11-20', TRUE),
('aaaaaaaa-0003-0000-0000-000000000006', 'Pharm. Nana Yaa Boadi', 'Pharmacist', 'PSGH-5190', '0244000666', 'nanayaa.boadi@adompharmacy.com', '$2b$10$CMbm7vNg/GHEK3QidZyVNeuws/EpWeBE3nPl9I.8bJq2mCDCWISIW', TRUE, '2026-07-19', TRUE);

-- Drugs (table code 0004)
-- 62 drugs total, 8 controlled substances, spanning common pharmacy categories:
-- analgesics, antibiotics, antimalarials, cardiovascular, diabetes, respiratory,
-- dermatological, GI, antihistamines, vitamins/supplements, and controlled substances.
INSERT INTO Drug (id, name, generic_name, dosage_form, strength, unit_price, is_controlled_substance, reorder_threshold) VALUES
('aaaaaaaa-0004-0000-0000-000000000001', 'Paracetamol', 'Acetaminophen', 'Tablet', '500mg', 0.15, FALSE, 200),
('aaaaaaaa-0004-0000-0000-000000000002', 'Amoxicillin', 'Amoxicillin', 'Capsule', '250mg', 0.35, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000003', 'Diazepam', 'Diazepam', 'Tablet', '5mg', 0.5, TRUE, 50),
('aaaaaaaa-0004-0000-0000-000000000004', 'Insulin Glargine', 'Insulin Glargine', 'Injection', '100units/ml', 45.0, FALSE, 20),
('aaaaaaaa-0004-0000-0000-000000000005', 'Amoxiclav Syrup', 'Amoxicillin/Clavulanate', 'Syrup', '125mg/5ml', 12.0, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000006', 'Tramadol', 'Tramadol HCl', 'Capsule', '50mg', 0.8, TRUE, 60),
('aaaaaaaa-0004-0000-0000-000000000007', 'Metformin', 'Metformin HCl', 'Tablet', '500mg', 0.2, FALSE, 200),
('aaaaaaaa-0004-0000-0000-000000000008', 'Ibuprofen', 'Ibuprofen', 'Tablet', '400mg', 0.18, FALSE, 200),
('aaaaaaaa-0004-0000-0000-000000000009', 'Aspirin', 'Acetylsalicylic Acid', 'Tablet', '75mg', 0.1, FALSE, 250),
('aaaaaaaa-0004-0000-0000-000000000010', 'Diclofenac', 'Diclofenac Sodium', 'Tablet', '50mg', 0.22, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000011', 'Diclofenac Gel', 'Diclofenac Sodium', 'Cream', '1%', 8.5, FALSE, 25),
('aaaaaaaa-0004-0000-0000-000000000012', 'Ciprofloxacin', 'Ciprofloxacin', 'Tablet', '500mg', 0.45, FALSE, 120),
('aaaaaaaa-0004-0000-0000-000000000013', 'Azithromycin', 'Azithromycin', 'Tablet', '250mg', 1.2, FALSE, 90),
('aaaaaaaa-0004-0000-0000-000000000014', 'Doxycycline', 'Doxycycline Hyclate', 'Capsule', '100mg', 0.4, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000015', 'Cefuroxime', 'Cefuroxime Axetil', 'Tablet', '500mg', 1.8, FALSE, 80),
('aaaaaaaa-0004-0000-0000-000000000016', 'Metronidazole', 'Metronidazole', 'Tablet', '400mg', 0.25, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000017', 'Erythromycin Syrup', 'Erythromycin', 'Syrup', '125mg/5ml', 9.0, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000018', 'Artemether/Lumefantrine', 'Artemether/Lumefantrine', 'Tablet', '20mg/120mg', 3.5, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000019', 'Artesunate Injection', 'Artesunate', 'Injection', '60mg', 15.0, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000020', 'Quinine Sulfate', 'Quinine Sulfate', 'Tablet', '300mg', 0.6, FALSE, 60),
('aaaaaaaa-0004-0000-0000-000000000021', 'Amlodipine', 'Amlodipine Besylate', 'Tablet', '5mg', 0.3, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000022', 'Lisinopril', 'Lisinopril', 'Tablet', '10mg', 0.35, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000023', 'Losartan', 'Losartan Potassium', 'Tablet', '50mg', 0.4, FALSE, 120),
('aaaaaaaa-0004-0000-0000-000000000024', 'Atenolol', 'Atenolol', 'Tablet', '50mg', 0.25, FALSE, 120),
('aaaaaaaa-0004-0000-0000-000000000025', 'Hydrochlorothiazide', 'Hydrochlorothiazide', 'Tablet', '25mg', 0.15, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000026', 'Furosemide', 'Furosemide', 'Tablet', '40mg', 0.18, FALSE, 120),
('aaaaaaaa-0004-0000-0000-000000000027', 'Atorvastatin', 'Atorvastatin Calcium', 'Tablet', '20mg', 0.55, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000028', 'Simvastatin', 'Simvastatin', 'Tablet', '20mg', 0.45, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000029', 'Glibenclamide', 'Glibenclamide', 'Tablet', '5mg', 0.2, FALSE, 120),
('aaaaaaaa-0004-0000-0000-000000000030', 'Gliclazide', 'Gliclazide', 'Tablet', '80mg', 0.35, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000031', 'Insulin Mixtard', 'Isophane/Regular Insulin', 'Injection', '70/30 100units/ml', 42.0, FALSE, 20),
('aaaaaaaa-0004-0000-0000-000000000032', 'Salbutamol Inhaler', 'Salbutamol', 'Other', '100mcg/dose', 18.0, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000033', 'Salbutamol Syrup', 'Salbutamol', 'Syrup', '2mg/5ml', 7.5, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000034', 'Prednisolone', 'Prednisolone', 'Tablet', '5mg', 0.2, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000035', 'Hydrocortisone Cream', 'Hydrocortisone', 'Cream', '1%', 6.0, FALSE, 25),
('aaaaaaaa-0004-0000-0000-000000000036', 'Betamethasone Cream', 'Betamethasone Valerate', 'Cream', '0.1%', 7.0, FALSE, 25),
('aaaaaaaa-0004-0000-0000-000000000037', 'Clotrimazole Cream', 'Clotrimazole', 'Cream', '1%', 5.5, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000038', 'Fluconazole', 'Fluconazole', 'Capsule', '150mg', 2.0, FALSE, 60),
('aaaaaaaa-0004-0000-0000-000000000039', 'Omeprazole', 'Omeprazole', 'Capsule', '20mg', 0.4, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000040', 'Esomeprazole', 'Esomeprazole', 'Tablet', '40mg', 0.75, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000041', 'Ranitidine', 'Ranitidine', 'Tablet', '150mg', 0.2, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000042', 'Loperamide', 'Loperamide HCl', 'Capsule', '2mg', 0.15, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000043', 'Oral Rehydration Salts', 'ORS', 'Other', '20.5g sachet', 1.5, FALSE, 100),
('aaaaaaaa-0004-0000-0000-000000000044', 'Cetirizine', 'Cetirizine HCl', 'Tablet', '10mg', 0.2, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000045', 'Loratadine', 'Loratadine', 'Tablet', '10mg', 0.25, FALSE, 120),
('aaaaaaaa-0004-0000-0000-000000000046', 'Chlorpheniramine', 'Chlorpheniramine Maleate', 'Tablet', '4mg', 0.1, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000047', 'Promethazine Syrup', 'Promethazine HCl', 'Syrup', '5mg/5ml', 6.5, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000048', 'Vitamin C', 'Ascorbic Acid', 'Tablet', '500mg', 0.15, FALSE, 200),
('aaaaaaaa-0004-0000-0000-000000000049', 'Multivitamin Syrup', 'Multivitamin', 'Syrup', '5ml dose', 8.0, FALSE, 40),
('aaaaaaaa-0004-0000-0000-000000000050', 'Folic Acid', 'Folic Acid', 'Tablet', '5mg', 0.08, FALSE, 200),
('aaaaaaaa-0004-0000-0000-000000000051', 'Ferrous Sulfate', 'Ferrous Sulfate', 'Tablet', '200mg', 0.1, FALSE, 200),
('aaaaaaaa-0004-0000-0000-000000000052', 'Calcium Carbonate', 'Calcium Carbonate', 'Tablet', '500mg', 0.18, FALSE, 150),
('aaaaaaaa-0004-0000-0000-000000000053', 'Zinc Sulfate Syrup', 'Zinc Sulfate', 'Syrup', '10mg/5ml', 5.5, FALSE, 40),
('aaaaaaaa-0004-0000-0000-000000000054', 'Morphine Sulfate', 'Morphine Sulfate', 'Injection', '10mg/ml', 6.5, TRUE, 20),
('aaaaaaaa-0004-0000-0000-000000000055', 'Codeine Phosphate', 'Codeine Phosphate', 'Tablet', '30mg', 0.9, TRUE, 40),
('aaaaaaaa-0004-0000-0000-000000000056', 'Lorazepam', 'Lorazepam', 'Tablet', '1mg', 0.6, TRUE, 40),
('aaaaaaaa-0004-0000-0000-000000000057', 'Alprazolam', 'Alprazolam', 'Tablet', '0.5mg', 0.55, TRUE, 40),
('aaaaaaaa-0004-0000-0000-000000000058', 'Pethidine', 'Pethidine HCl', 'Injection', '50mg/ml', 7.0, TRUE, 15),
('aaaaaaaa-0004-0000-0000-000000000059', 'Phenobarbital', 'Phenobarbital', 'Tablet', '30mg', 0.3, TRUE, 30),
('aaaaaaaa-0004-0000-0000-000000000060', 'Amoxicillin Pediatric Drops', 'Amoxicillin', 'Syrup', '125mg/1.25ml', 10.5, FALSE, 30),
('aaaaaaaa-0004-0000-0000-000000000061', 'Paracetamol Syrup', 'Acetaminophen', 'Syrup', '120mg/5ml', 6.0, FALSE, 60),
('aaaaaaaa-0004-0000-0000-000000000062', 'Ibuprofen Syrup', 'Ibuprofen', 'Syrup', '100mg/5ml', 6.5, FALSE, 50);

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
-- Order 2 (Supplier 2): Diazepam, Tramadol, Morphine Sulfate, Lorazepam — all controlled substances
-- (Morphine Sulfate and Lorazepam are deliberately left unverified — see Batch notes below)
INSERT INTO PurchaseOrderItem (id, purchase_order_id, drug_id, quantity_ordered, unit_cost) VALUES
('aaaaaaaa-0007-0000-0000-000000000001', 'aaaaaaaa-0006-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000001', 500, 0.10),
('aaaaaaaa-0007-0000-0000-000000000002', 'aaaaaaaa-0006-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000002', 300, 0.25),
('aaaaaaaa-0007-0000-0000-000000000003', 'aaaaaaaa-0006-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000003', 100, 0.35),
('aaaaaaaa-0007-0000-0000-000000000004', 'aaaaaaaa-0006-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000006', 120, 0.55),
('aaaaaaaa-0007-0000-0000-000000000005', 'aaaaaaaa-0006-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000054', 60, 4.55),
('aaaaaaaa-0007-0000-0000-000000000006', 'aaaaaaaa-0006-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000056', 120, 0.42);

-- Batches (table code 0008)
-- Batches 1-7 are the original demo set (see inline PO/verification notes below).
-- Batches 8+ (55 new, one per expanded drug) are mostly manual stock entries
-- (purchase_order_item_id NULL) EXCEPT Morphine Sulfate and Lorazepam, which are
-- linked to the two new PurchaseOrderItem rows above and deliberately left with
-- verified_by_pharmacist_id NULL — fresh, realistic data for testing
-- GET /purchase-orders/awaiting-verification (Rule 11, Rule 17) beyond the
-- original Diazepam/Tramadol demo pair, which stay verified as before.
INSERT INTO Batch (id, drug_id, batch_number, quantity_in_stock, expiry_date, date_received, supplier_id, purchase_order_item_id, verified_by_pharmacist_id) VALUES
('aaaaaaaa-0008-0000-0000-000000000001', 'aaaaaaaa-0004-0000-0000-000000000001', 'PARA-2026-01', 500, '2027-12-31', '2026-01-15', 'aaaaaaaa-0005-0000-0000-000000000001', 'aaaaaaaa-0007-0000-0000-000000000001', NULL),
('aaaaaaaa-0008-0000-0000-000000000002', 'aaaaaaaa-0004-0000-0000-000000000002', 'AMOX-2026-02', 300, '2027-06-30', '2026-02-10', 'aaaaaaaa-0005-0000-0000-000000000001', 'aaaaaaaa-0007-0000-0000-000000000002', NULL),
('aaaaaaaa-0008-0000-0000-000000000003', 'aaaaaaaa-0004-0000-0000-000000000003', 'DIAZ-2026-01', 100, '2027-03-31', '2026-01-20', 'aaaaaaaa-0005-0000-0000-000000000002', 'aaaaaaaa-0007-0000-0000-000000000003', 'aaaaaaaa-0003-0000-0000-000000000001'),
('aaaaaaaa-0008-0000-0000-000000000004', 'aaaaaaaa-0004-0000-0000-000000000004', 'INSU-2026-01', 40, '2026-11-30', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000005', 'aaaaaaaa-0004-0000-0000-000000000005', 'AMSYR-2026-01', 60, '2027-01-31', '2026-02-15', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000006', 'aaaaaaaa-0004-0000-0000-000000000006', 'TRAM-2026-01', 120, '2027-05-31', '2026-01-25', 'aaaaaaaa-0005-0000-0000-000000000002', 'aaaaaaaa-0007-0000-0000-000000000004', 'aaaaaaaa-0003-0000-0000-000000000002'),
('aaaaaaaa-0008-0000-0000-000000000007', 'aaaaaaaa-0004-0000-0000-000000000007', 'METF-2026-01', 400, '2027-09-30', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000008', 'aaaaaaaa-0004-0000-0000-000000000008', 'IBUP-2026-09', 600, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000009', 'aaaaaaaa-0004-0000-0000-000000000009', 'ASPI-2026-10', 750, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000010', 'aaaaaaaa-0004-0000-0000-000000000010', 'DICL-2026-11', 450, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000011', 'aaaaaaaa-0004-0000-0000-000000000011', 'DICL-2026-12', 75, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000012', 'aaaaaaaa-0004-0000-0000-000000000012', 'CIPR-2026-01', 360, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000013', 'aaaaaaaa-0004-0000-0000-000000000013', 'AZIT-2026-02', 270, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000014', 'aaaaaaaa-0004-0000-0000-000000000014', 'DOXY-2026-03', 300, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000015', 'aaaaaaaa-0004-0000-0000-000000000015', 'CEFU-2026-04', 240, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000016', 'aaaaaaaa-0004-0000-0000-000000000016', 'METR-2026-05', 450, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000017', 'aaaaaaaa-0004-0000-0000-000000000017', 'ERYT-2026-06', 90, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000018', 'aaaaaaaa-0004-0000-0000-000000000018', 'ARTE-2026-07', 300, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000019', 'aaaaaaaa-0004-0000-0000-000000000019', 'ARTE-2026-08', 90, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000020', 'aaaaaaaa-0004-0000-0000-000000000020', 'QUIN-2026-09', 180, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000021', 'aaaaaaaa-0004-0000-0000-000000000021', 'AMLO-2026-10', 450, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000022', 'aaaaaaaa-0004-0000-0000-000000000022', 'LISI-2026-11', 450, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000023', 'aaaaaaaa-0004-0000-0000-000000000023', 'LOSA-2026-12', 360, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000024', 'aaaaaaaa-0004-0000-0000-000000000024', 'ATEN-2026-01', 360, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000025', 'aaaaaaaa-0004-0000-0000-000000000025', 'HYDR-2026-02', 450, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000026', 'aaaaaaaa-0004-0000-0000-000000000026', 'FURO-2026-03', 360, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000027', 'aaaaaaaa-0004-0000-0000-000000000027', 'ATOR-2026-04', 300, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000028', 'aaaaaaaa-0004-0000-0000-000000000028', 'SIMV-2026-05', 300, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000029', 'aaaaaaaa-0004-0000-0000-000000000029', 'GLIB-2026-06', 360, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000030', 'aaaaaaaa-0004-0000-0000-000000000030', 'GLIC-2026-07', 300, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000031', 'aaaaaaaa-0004-0000-0000-000000000031', 'INSU-2026-08', 60, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000032', 'aaaaaaaa-0004-0000-0000-000000000032', 'SALB-2026-09', 90, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000033', 'aaaaaaaa-0004-0000-0000-000000000033', 'SALB-2026-10', 90, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000034', 'aaaaaaaa-0004-0000-0000-000000000034', 'PRED-2026-11', 300, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000035', 'aaaaaaaa-0004-0000-0000-000000000035', 'HYDR-2026-12', 75, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000036', 'aaaaaaaa-0004-0000-0000-000000000036', 'BETA-2026-01', 75, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000037', 'aaaaaaaa-0004-0000-0000-000000000037', 'CLOT-2026-02', 90, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000038', 'aaaaaaaa-0004-0000-0000-000000000038', 'FLUC-2026-03', 180, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000039', 'aaaaaaaa-0004-0000-0000-000000000039', 'OMEP-2026-04', 450, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000040', 'aaaaaaaa-0004-0000-0000-000000000040', 'ESOM-2026-05', 300, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000041', 'aaaaaaaa-0004-0000-0000-000000000041', 'RANI-2026-06', 300, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000042', 'aaaaaaaa-0004-0000-0000-000000000042', 'LOPE-2026-07', 300, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000043', 'aaaaaaaa-0004-0000-0000-000000000043', 'ORAL-2026-08', 300, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000044', 'aaaaaaaa-0004-0000-0000-000000000044', 'CETI-2026-09', 450, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000045', 'aaaaaaaa-0004-0000-0000-000000000045', 'LORA-2026-10', 360, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000046', 'aaaaaaaa-0004-0000-0000-000000000046', 'CHLO-2026-11', 450, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000047', 'aaaaaaaa-0004-0000-0000-000000000047', 'PROM-2026-12', 90, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000048', 'aaaaaaaa-0004-0000-0000-000000000048', 'VITA-2026-01', 600, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000049', 'aaaaaaaa-0004-0000-0000-000000000049', 'MULT-2026-02', 120, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000050', 'aaaaaaaa-0004-0000-0000-000000000050', 'FOLI-2026-03', 600, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000051', 'aaaaaaaa-0004-0000-0000-000000000051', 'FERR-2026-04', 600, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000052', 'aaaaaaaa-0004-0000-0000-000000000052', 'CALC-2026-05', 450, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000053', 'aaaaaaaa-0004-0000-0000-000000000053', 'ZINC-2026-06', 120, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000054', 'aaaaaaaa-0004-0000-0000-000000000054', 'MORP-2026-07', 60, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', 'aaaaaaaa-0007-0000-0000-000000000005', NULL),
('aaaaaaaa-0008-0000-0000-000000000055', 'aaaaaaaa-0004-0000-0000-000000000055', 'CODE-2026-08', 120, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000056', 'aaaaaaaa-0004-0000-0000-000000000056', 'LORA-2026-09', 120, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', 'aaaaaaaa-0007-0000-0000-000000000006', NULL),
('aaaaaaaa-0008-0000-0000-000000000057', 'aaaaaaaa-0004-0000-0000-000000000057', 'ALPR-2026-10', 120, '2028-03-31', '2026-03-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000058', 'aaaaaaaa-0004-0000-0000-000000000058', 'PETH-2026-11', 45, '2028-06-30', '2026-03-15', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000059', 'aaaaaaaa-0004-0000-0000-000000000059', 'PHEN-2026-12', 90, '2028-09-30', '2026-04-01', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000060', 'aaaaaaaa-0004-0000-0000-000000000060', 'AMOX-2026-01', 90, '2027-06-30', '2026-01-10', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000061', 'aaaaaaaa-0004-0000-0000-000000000061', 'PARA-2026-02', 180, '2027-09-30', '2026-02-05', 'aaaaaaaa-0005-0000-0000-000000000002', NULL, NULL),
('aaaaaaaa-0008-0000-0000-000000000062', 'aaaaaaaa-0004-0000-0000-000000000062', 'IBUP-2026-03', 150, '2027-12-31', '2026-02-20', 'aaaaaaaa-0005-0000-0000-000000000001', NULL, NULL);

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
