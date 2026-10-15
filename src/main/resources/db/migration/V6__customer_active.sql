-- =====================================================================
-- V6__customer_active.sql · Logical deletion for CUSTOMER (RF-06)
-- Mirrors V2 (PRODUCT), V4 (CATEGORY) and V5 (SUPPLIER): a customer is
-- never deleted physically because SALES_ORDER references it with
-- ON DELETE RESTRICT, so a customer with orders could not be removed.
-- The UNIQUE (name) mirrors CATEGORY/SUPPLIER: an inactive customer
-- still reserves its name, so it has to be reactivated instead of
-- duplicated.
-- =====================================================================

ALTER TABLE CUSTOMER
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1,
    ADD CONSTRAINT uq_customer_name UNIQUE (name),
    ADD INDEX idx_customer_active (active, name);
