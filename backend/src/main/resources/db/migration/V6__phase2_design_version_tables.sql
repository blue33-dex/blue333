-- Phase 2 (P2-T01): design versions, version files, internal review records, release records.
-- File name differs from the task sheet's "V3__" because V3-V5 are already used by Phase 1 fixes.

CREATE TABLE design_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    version_no VARCHAR(10) NOT NULL,
    design_note TEXT NOT NULL,
    change_summary TEXT,
    uploader_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    is_final TINYINT(1) NOT NULL DEFAULT 0,
    is_overdue TINYINT(1) NOT NULL DEFAULT 0,
    submitted_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_dver_task_id_version_no UNIQUE (task_id, version_no),
    CONSTRAINT fk_design_versions_task_id FOREIGN KEY (task_id) REFERENCES material_tasks (id),
    CONSTRAINT fk_design_versions_uploader_id FOREIGN KEY (uploader_id) REFERENCES users (id),
    INDEX idx_design_versions_status_submitted_at (status, submitted_at),
    INDEX idx_design_versions_uploader_id (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE version_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_version_files_version_id UNIQUE (version_id),
    CONSTRAINT uk_version_files_storage_path UNIQUE (storage_path),
    CONSTRAINT fk_version_files_version_id FOREIGN KEY (version_id) REFERENCES design_versions (id),
    CONSTRAINT chk_version_files_file_size CHECK (file_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    review_result VARCHAR(10) NOT NULL,
    review_comment TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_review_records_version_id FOREIGN KEY (version_id) REFERENCES design_versions (id),
    CONSTRAINT fk_review_records_task_id FOREIGN KEY (task_id) REFERENCES material_tasks (id),
    CONSTRAINT fk_review_records_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES users (id),
    INDEX idx_review_records_version_id_created_at (version_id, created_at),
    INDEX idx_review_records_task_id_created_at (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE release_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    releaser_id BIGINT NOT NULL,
    release_remark TEXT,
    released_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_release_records_version_id UNIQUE (version_id),
    CONSTRAINT fk_release_records_version_id FOREIGN KEY (version_id) REFERENCES design_versions (id),
    CONSTRAINT fk_release_records_releaser_id FOREIGN KEY (releaser_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
