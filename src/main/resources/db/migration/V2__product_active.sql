-- =====================================================================
-- V2__product_active.sql · Logical deletion for PRODUCT (RF-03)
-- Mirrors USER.active: "deleting" a product only sets active = 0, so the
-- foreign keys of STOCK and SALES_ORDER_LINE (ON DELETE RESTRICT) stay valid
-- and the purchase/sales history is preserved.
-- The composite index supports the list queries, which always filter by
-- active and order by name.
-- =====================================================================

ALTER TABLE PRODUCT
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1,
    ADD INDEX idx_product_active (active, name);
