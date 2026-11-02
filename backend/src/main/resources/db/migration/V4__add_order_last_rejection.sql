-- BO-02: persist the most recent rejection so AM and BO can see why an order was returned.
ALTER TABLE orders
    ADD COLUMN last_reject_reason_type VARCHAR(30) NULL,
    ADD COLUMN last_reject_remark TEXT NULL,
    ADD COLUMN last_rejected_by BIGINT NULL,
    ADD COLUMN last_rejected_at DATETIME(3) NULL,
    ADD CONSTRAINT fk_orders_last_rejected_by FOREIGN KEY (last_rejected_by) REFERENCES users (id);
