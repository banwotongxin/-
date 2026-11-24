-- H2 演示库建表脚本（MODE=MySQL，语法与 MySQL 8 兼容）
-- 使用 IF NOT EXISTS 保证重复启动幂等

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    student_no VARCHAR(32),
    status VARCHAR(20) NOT NULL,
    last_login_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_student_no UNIQUE (student_no)
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at DATETIME,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME,
    CONSTRAINT uk_user_role_pair UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS clubs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    intro VARCHAR(4000),
    owner_user_id BIGINT,
    contact VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT uk_club_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS club_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at DATETIME,
    CONSTRAINT uk_club_member_pair UNIQUE (club_id, user_id)
);

CREATE TABLE IF NOT EXISTS club_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    club_name VARCHAR(100) NOT NULL,
    content VARCHAR(4000),
    contact VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    reviewer_id BIGINT,
    review_reason VARCHAR(255),
    reviewed_at DATETIME,
    created_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_club_application_status ON club_applications (status, created_at);

CREATE TABLE IF NOT EXISTS activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    club_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    summary VARCHAR(4000) NOT NULL,
    location VARCHAR(200),
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    signup_start DATETIME NOT NULL,
    signup_end DATETIME NOT NULL,
    capacity INT NOT NULL,
    signed_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    contact VARCHAR(100),
    takedown_reason VARCHAR(255),
    takedown_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_activity_club_status ON activities (club_id, status);
CREATE INDEX IF NOT EXISTS idx_activity_signup_window ON activities (status, signup_start, signup_end);
CREATE INDEX IF NOT EXISTS idx_activity_list ON activities (status, start_time, id);

CREATE TABLE IF NOT EXISTS activity_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    tag VARCHAR(50) NOT NULL,
    CONSTRAINT uk_activity_tag_pair UNIQUE (activity_id, tag)
);

CREATE TABLE IF NOT EXISTS signups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    remark VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    reviewer_id BIGINT,
    review_reason VARCHAR(500),
    reviewed_at DATETIME,
    cancelled_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT uk_signup_pair UNIQUE (activity_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_signup_activity_status ON signups (activity_id, status, created_at);

CREATE TABLE IF NOT EXISTS signins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    signup_id BIGINT NOT NULL,
    signed_by BIGINT,
    signed_at DATETIME,
    source VARCHAR(20),
    remark VARCHAR(500),
    created_at DATETIME,
    CONSTRAINT uk_signin_signup UNIQUE (signup_id)
);

CREATE INDEX IF NOT EXISTS idx_signin_activity ON signins (activity_id);

CREATE TABLE IF NOT EXISTS signin_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    signup_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    signed_at DATETIME NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewer_id BIGINT,
    review_reason VARCHAR(500),
    reviewed_at DATETIME,
    created_at DATETIME,
    CONSTRAINT uk_signin_application_signup UNIQUE (signup_id)
);

CREATE INDEX IF NOT EXISTS idx_signin_application_activity_status
    ON signin_applications (activity_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_signin_application_student
    ON signin_applications (student_id, created_at);

CREATE TABLE IF NOT EXISTS archives (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    uploader_id BIGINT,
    file_id BIGINT NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME,
    CONSTRAINT uk_archive_activity_version UNIQUE (activity_id, version)
);

CREATE INDEX IF NOT EXISTS idx_archive_activity ON archives (activity_id, created_at);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    msg_type VARCHAR(30),
    title VARCHAR(150),
    content VARCHAR(2000),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_message_recipient_read ON messages (recipient_id, is_read, created_at);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(64),
    operator_id BIGINT,
    resource_type VARCHAR(50),
    resource_id BIGINT,
    action VARCHAR(50),
    before_json VARCHAR(4000),
    after_json VARCHAR(4000),
    result_mark VARCHAR(20),
    created_at DATETIME
);

CREATE INDEX IF NOT EXISTS idx_audit_resource ON audit_logs (resource_type, resource_id, created_at);

CREATE TABLE IF NOT EXISTS file_objects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    object_key VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    mime_type VARCHAR(100),
    size_bytes BIGINT,
    sha256 VARCHAR(64),
    created_at DATETIME,
    CONSTRAINT uk_file_object_key UNIQUE (object_key)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(120) NOT NULL,
    recipient_id BIGINT,
    channel VARCHAR(20),
    status VARCHAR(20),
    created_at DATETIME,
    CONSTRAINT uk_notification_event UNIQUE (event_key)
);
