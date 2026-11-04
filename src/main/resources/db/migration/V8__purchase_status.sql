-- =====================================================================
-- V8__purchase_status.sql · Purchase state machine (RF-14 extension)
-- Adds the "returned" state so a received purchase can be given back to the
-- supplier without being cancelled. The transition rules live in the domain
-- (Purchase.allowedTransitions): pending -> received/cancelled and
-- received -> returned; received purchases can never be cancelled.
-- "returned" is appended at the end so the existing ENUM values keep their
-- order. Rows already stored as "received" remain valid.
-- =====================================================================

ALTER TABLE PURCHASE
    MODIFY status ENUM ('pending', 'received', 'cancelled', 'returned') NOT NULL DEFAULT 'pending';
