-- Phase 1 development seed data.
-- All three accounts use the BCrypt hash for the local-only test password: password
-- Replace or remove these accounts before any non-development deployment.

INSERT INTO roles (id, role_type, role_name) VALUES
    (1, 'AM', '客户经理'),
    (2, 'BO', '业务负责人'),
    (3, 'DS', '设计制作人员');

INSERT INTO users (id, username, password_hash, name, email, phone, enabled) VALUES
    (1, 'am_demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张晓明', 'zhang.xiaoming@example.com', '13812348888', 1),
    (2, 'bo_demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王审核', 'wang.shenhe@example.com', '13912345678', 1),
    (3, 'ds_demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李设计', 'li.sheji@example.com', '13712345678', 1);

INSERT INTO user_roles (user_id, role_id) VALUES
    (1, 1),
    (2, 2),
    (3, 3);
