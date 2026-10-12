-- =====================================================================
-- V4__category_active.sql · Logical deletion for CATEGORY (RF-04)
-- Mirrors USER.active / PRODUCT.active: "deleting" a category only sets
-- active = 0, so the foreign key of PRODUCT (ON DELETE RESTRICT) stays valid
-- and the products that already referenced it are not touched.
-- A category that is no longer offered cannot be assigned to new products,
-- but it is still listed (and filterable) with the products that keep it.
-- The composite index supports the list queries, which always filter by
-- active and order by name.
-- =====================================================================

ALTER TABLE CATEGORY
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1,
    ADD INDEX idx_category_active (active, name);
