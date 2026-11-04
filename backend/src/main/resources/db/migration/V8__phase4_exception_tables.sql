-- Phase 4: exception records (E-18) used by A-20/A-21 and by the automatic overdue-task scan.
-- The task list names V5, but V3~V7 are already taken by Phase 1/2/3 migrations.
--
-- recorder_id is nullable on purpose: records created by the scheduled overdue scan have no human
-- recorder, and attributing them to an arbitrary account would falsify the audit trail. Manual
-- records always carry the acting BO.
-- task_id is nullable for manually recorded exceptions; the overdue scan sets it so the same task
-- never raises a duplicate DELAY record.

CREATE TABLE exception_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    exception_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    resolution TEXT NULL,
    recorder_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_exception_records_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_exception_records_task_id FOREIGN KEY (task_id) REFERENCES material_tasks (id),
    CONSTRAINT fk_exception_records_recorder_id FOREIGN KEY (recorder_id) REFERENCES users (id),
    INDEX idx_exception_records_order_id_status (order_id, status),
    INDEX idx_exception_records_status_created_at (status, created_at),
    INDEX idx_exception_records_task_id_type (task_id, exception_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
