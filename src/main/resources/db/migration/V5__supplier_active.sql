-- =====================================================================
-- V5__supplier_active.sql · Logical deletion for SUPPLIER (RF-05)
-- Mirrors V2 (PRODUCT) and V4 (CATEGORY): a supplier is never deleted
-- physically because PURCHASE references it with ON DELETE RESTRICT.
-- The UNIQUE (name) mirrors CATEGORY/USER/PRODUCT: an inactive supplier
-- still reserves its name, so it has to be reactivated instead of
-- duplicated.
-- =====================================================================

ALTER TABLE SUPPLIER
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1,
    ADD CONSTRAINT uq_supplier_name UNIQUE (name),
    ADD INDEX idx_supplier_active (active, name);
