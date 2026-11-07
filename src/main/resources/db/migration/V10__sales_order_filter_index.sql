-- =====================================================================
-- V10__sales_order_filter_index.sql · Sales-order history filters (RF-07)
-- The sales-order screen filters the history by date range and status, and
-- orders it by order_date DESC: the composite index serves both the
-- guards and the ORDER BY. SALES_ORDER.customer_id is already indexed by
-- its foreign key, so the customer filter needs no extra index.
-- =====================================================================

ALTER TABLE SALES_ORDER
    ADD INDEX idx_order_date_status (order_date, status);
