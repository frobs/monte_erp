-- =====================================================================
-- V9__purchase_filter_index.sql · Purchase history filters (RF-14)
-- The purchases screen filters the history by date range and status, and
-- orders it by purchase_date DESC: the composite index serves both the
-- guards and the ORDER BY. PURCHASE.supplier_id is already indexed by its
-- foreign key, so the supplier filter needs no extra index.
-- =====================================================================

ALTER TABLE PURCHASE
    ADD INDEX idx_purchase_date_status (purchase_date, status);
