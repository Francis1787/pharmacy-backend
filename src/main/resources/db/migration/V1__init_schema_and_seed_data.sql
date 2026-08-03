-- =========================================================
-- Adom Community Pharmacy — Database Schema (DDL only)
-- RDBMS: PostgreSQL 14+ (gen_random_uuid() is native since PG13, no extension needed)
--
-- This migration runs in EVERY environment (dev, staging, prod).
-- It creates the schema only — no data. Demo/mock data lives in
-- V2__seed_dev_data.sql, which is dev-only and should never be
-- applied against a production database.
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
-- NOTE ON UUID PRIMARY KEYS: all IDs are UUID, generated via
-- gen_random_uuid() by default, or supplied client-side by the
-- application layer before insert.
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

