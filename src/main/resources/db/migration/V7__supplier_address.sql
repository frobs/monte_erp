-- =====================================================================
-- V7__supplier_address.sql · Postal address for SUPPLIER (RF-05)
-- Mirrors CUSTOMER.address (V1__schema.sql): the address is optional and
-- is shown/edited in the supplier form, just like in the customer form.
-- The column is added after email so the column order matches CUSTOMER
-- (V5 already added active before this migration).
-- =====================================================================

ALTER TABLE SUPPLIER
    ADD COLUMN address VARCHAR(255) NULL AFTER email;
