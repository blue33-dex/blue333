-- Phase 1 schema: identity, order, task, audit, and notification tables.
-- Naming and storage conventions follow docs/01-requirements/GLOSSARY.md.

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_type VARCHAR(5) NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_role_type UNIQUE (role_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(32),
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(20) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    use_scene TEXT NOT NULL,
    delivery_deadline DATE NOT NULL,
    remark TEXT,
    status VARCHAR(20) NOT NULL,
    am_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_no UNIQUE (order_no),
    CONSTRAINT fk_orders_am_id FOREIGN KEY (am_id) REFERENCES users (id),
    INDEX idx_orders_am_id_created_at (am_id, created_at),
    INDEX idx_orders_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE material_specs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    material_type VARCHAR(20) NOT NULL,
    spec_size VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_material_specs_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_material_specs_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE requirement_baselines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    baseline_version INT NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    use_scene TEXT NOT NULL,
    delivery_deadline DATE NOT NULL,
    remark TEXT,
    material_specs_snapshot JSON NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_requirement_baselines_order_version UNIQUE (order_id, baseline_version),
    CONSTRAINT fk_requirement_baselines_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_requirement_baselines_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_requirement_baselines_version CHECK (baseline_version > 0),
    INDEX idx_requirement_baselines_order_id_created_at (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_attachments_storage_path UNIQUE (storage_path),
    CONSTRAINT fk_attachments_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_attachments_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT chk_attachments_file_size CHECK (file_size >= 0),
    INDEX idx_attachments_order_id_created_at (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE material_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    material_spec_id BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    task_deadline DATETIME(3) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_material_tasks_material_spec_id UNIQUE (material_spec_id),
    CONSTRAINT fk_material_tasks_order_id FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_material_tasks_material_spec_id FOREIGN KEY (material_spec_id) REFERENCES material_specs (id),
    CONSTRAINT fk_material_tasks_assignee_id FOREIGN KEY (assignee_id) REFERENCES users (id),
    INDEX idx_material_tasks_assignee_id_status_deadline (assignee_id, status, task_deadline),
    INDEX idx_material_tasks_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    assigner_id BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    task_deadline DATETIME(3) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    remark TEXT,
    assignment_type VARCHAR(10) NOT NULL,
    previous_assignee_id BIGINT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_assignments_task_id FOREIGN KEY (task_id) REFERENCES material_tasks (id),
    CONSTRAINT fk_assignments_assigner_id FOREIGN KEY (assigner_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_assignee_id FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_assignments_previous_assignee_id FOREIGN KEY (previous_assignee_id) REFERENCES users (id),
    INDEX idx_assignments_task_id_created_at (task_id, created_at),
    INDEX idx_assignments_assignee_id_created_at (assignee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    before_state VARCHAR(30),
    after_state VARCHAR(30),
    operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    result VARCHAR(20) NOT NULL,
    remark TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_operator_id FOREIGN KEY (operator_id) REFERENCES users (id),
    INDEX idx_audit_logs_target_type_target_id_operated_at (target_type, target_id, operated_at),
    INDEX idx_audit_logs_operator_id_operated_at (operator_id, operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    notification_type VARCHAR(20) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    read_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient_id FOREIGN KEY (recipient_id) REFERENCES users (id),
    CONSTRAINT chk_notifications_read_at CHECK ((is_read = 0 AND read_at IS NULL) OR (is_read = 1 AND read_at IS NOT NULL)),
    INDEX idx_notif_recipient_id_is_read (recipient_id, is_read),
    INDEX idx_notifications_recipient_id_created_at (recipient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
