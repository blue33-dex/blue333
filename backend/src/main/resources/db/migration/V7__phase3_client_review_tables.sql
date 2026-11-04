-- Phase 3 (P3-T01): client reviews, feedback items, confirmation records, delivery archives.
-- The task sheet names this file "V4__phase3_client_review_tables.sql"; V3-V6 are already used by
-- Phase 1 fixes and Phase 2, so this migration takes the next free Flyway version (V7).

CREATE TABLE client_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    initiator_id BIGINT NOT NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3),
    PRIMARY KEY (id),
    -- BR-08: one client-review session per released version.
    CONSTRAINT uk_client_reviews_version_id UNIQUE (version_id),
    CONSTRAINT fk_client_reviews_version_id FOREIGN KEY (version_id) REFERENCES design_versions (id),
    CONSTRAINT fk_client_reviews_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_client_reviews_initiator_id FOREIGN KEY (initiator_id) REFERENCES users (id),
    INDEX idx_client_reviews_order_id_status (order_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feedback_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feedback_no VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    source VARCHAR(20) NOT NULL,
    version_id BIGINT NOT NULL,
    client_review_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_feedback_items_feedback_no UNIQUE (feedback_no),
    -- BR-09: a feedback item can never exist without its design version.
    CONSTRAINT fk_feedback_items_version_id FOREIGN KEY (version_id) REFERENCES design_versions (id),
    CONSTRAINT fk_feedback_items_client_review_id FOREIGN KEY (client_review_id) REFERENCES client_reviews (id),
    CONSTRAINT fk_feedback_items_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_feedback_items_version_id_created_at (version_id, created_at),
    INDEX idx_feedback_items_client_review_id (client_review_id),
    INDEX idx_feedback_items_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE confirmation_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    confirmer_name VARCHAR(255) NOT NULL,
    confirmed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_confirmation_records_version_id UNIQUE (version_id),
    CONSTRAINT fk_confirmation_records_version_id FOREIGN KEY (version_id) REFERENCES design_versions (id),
    CONSTRAINT fk_confirmation_records_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_confirmation_records_order_id_confirmed_at (order_id, confirmed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BR-10: confirmation records are append-only. The guarantee is layered:
--   1. ConfirmationRecord is @Immutable and ConfirmationRecordRepository exposes only save/read;
--   2. the application never issues UPDATE/DELETE for this table;
--   3. in production the database account should be granted only INSERT/SELECT on this table.
-- MySQL 8 rejects CREATE TRIGGER for a non-SUPER account while binary logging is enabled, so the
-- trigger variant cannot be part of a Flyway migration run by the application account.

CREATE TABLE delivery_archives (
    id BIGINT NOT NULL AUTO_INCREMENT,
    archiver_id BIGINT NOT NULL,
    archived_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    order_id BIGINT NOT NULL,
    archive_report_path VARCHAR(512) NOT NULL,
    PRIMARY KEY (id),
    -- BR-11/BR-12: an order is archived exactly once and can never leave COMPLETED afterwards.
    CONSTRAINT uk_delivery_archives_order_id UNIQUE (order_id),
    CONSTRAINT uk_delivery_archives_report_path UNIQUE (archive_report_path),
    CONSTRAINT fk_delivery_archives_archiver_id FOREIGN KEY (archiver_id) REFERENCES users (id),
    CONSTRAINT fk_delivery_archives_order_id FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
