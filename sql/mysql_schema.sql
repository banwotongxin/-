-- ============================================================
-- 校园活动报名与社团信息管理系统 MySQL 8.0 建表脚本
-- 字符集：utf8mb4 / 排序规则：utf8mb4_general_ci
-- 说明：角色与初始账号由应用启动时的 DataInitializer 使用 BCrypt 创建，
--       不要在此脚本中写死密码哈希，文末仅保留参考用的插入语句注释。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS activity_tags;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS file_objects;
DROP TABLE IF EXISTS archives;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS signin_applications;
DROP TABLE IF EXISTS signins;
DROP TABLE IF EXISTS signups;
DROP TABLE IF EXISTS activities;
DROP TABLE IF EXISTS club_applications;
DROP TABLE IF EXISTS club_members;
DROP TABLE IF EXISTS clubs;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS user_account;

CREATE DATABASE IF NOT EXISTS campus DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 用户账号
CREATE TABLE user_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 摘要',
    real_name     VARCHAR(50)  NOT NULL COMMENT '姓名',
    student_no    VARCHAR(32)  NULL COMMENT '学号，学生必填',
    status        VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / FROZEN / DISABLED',
    last_login_at DATETIME     NULL COMMENT '最近登录时间',
    created_at    DATETIME     NULL COMMENT '创建时间',
    updated_at    DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_student_no (student_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户账号';

-- 角色
CREATE TABLE roles (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50) NOT NULL COMMENT '角色编码',
    name       VARCHAR(50) NOT NULL COMMENT '角色名称',
    created_at DATETIME    NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

-- 用户角色关联
CREATE TABLE user_roles (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT   NOT NULL COMMENT '用户 ID',
    role_id    BIGINT   NOT NULL COMMENT '角色 ID',
    created_at DATETIME NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_pair (user_id, role_id),
    KEY idx_user_role_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关联';

-- 社团
CREATE TABLE clubs (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(100) NOT NULL COMMENT '社团名称',
    intro         TEXT         NULL COMMENT '社团简介',
    owner_user_id BIGINT       NULL COMMENT '负责人用户 ID',
    contact       VARCHAR(100) NULL COMMENT '联系方式',
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING / ACTIVE / FROZEN / REJECTED',
    created_at    DATETIME     NULL COMMENT '创建时间',
    updated_at    DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_club_name (name),
    KEY idx_club_owner (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '社团';

-- 社团成员
CREATE TABLE club_members (
    id        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    club_id   BIGINT      NOT NULL COMMENT '社团 ID',
    user_id   BIGINT      NOT NULL COMMENT '用户 ID',
    member_role VARCHAR(20) NOT NULL COMMENT 'OWNER / MEMBER',
    status    VARCHAR(20) NOT NULL COMMENT 'ACTIVE / LEFT',
    joined_at DATETIME    NULL COMMENT '加入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_club_member_pair (club_id, user_id),
    KEY idx_club_member_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '社团成员';

-- 社团入驻申请
CREATE TABLE club_applications (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    applicant_id  BIGINT       NOT NULL COMMENT '申请人 ID',
    club_name     VARCHAR(100) NOT NULL COMMENT '社团名称',
    content       TEXT         NULL COMMENT '申请说明',
    contact       VARCHAR(100) NULL COMMENT '联系方式，仅管理员可见',
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING / APPROVED / REJECTED',
    reviewer_id   BIGINT       NULL COMMENT '审核人 ID',
    review_reason VARCHAR(255) NULL COMMENT '审核原因',
    reviewed_at   DATETIME     NULL COMMENT '审核时间',
    created_at    DATETIME     NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_club_application_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '社团入驻申请';

-- 活动
CREATE TABLE activities (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    club_id      BIGINT       NOT NULL COMMENT '所属社团',
    title        VARCHAR(150) NOT NULL COMMENT '标题',
    summary      TEXT         NOT NULL COMMENT '简介',
    location     VARCHAR(200) NULL COMMENT '活动地点',
    start_time   DATETIME     NOT NULL COMMENT '活动开始',
    end_time     DATETIME     NOT NULL COMMENT '活动结束',
    signup_start DATETIME     NOT NULL COMMENT '报名开始',
    signup_end   DATETIME     NOT NULL COMMENT '报名截止',
    capacity     INT          NOT NULL COMMENT '人数上限',
    signed_count INT          NOT NULL DEFAULT 0 COMMENT '已通过人数',
    status       VARCHAR(20)  NOT NULL COMMENT 'DRAFT / PUBLISHED / FULL / CLOSED / ARCHIVED',
    contact      VARCHAR(100) NULL COMMENT '活动联系方式',
    takedown_reason VARCHAR(255) NULL COMMENT '平台下架原因',
    takedown_at  DATETIME     NULL COMMENT '平台下架时间，非空表示待恢复',
    created_at   DATETIME     NULL COMMENT '创建时间',
    updated_at   DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_activity_club_status (club_id, status),
    KEY idx_activity_signup_window (status, signup_start, signup_end),
    KEY idx_activity_list (status, start_time, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '活动';

-- 活动标签
CREATE TABLE activity_tags (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT      NOT NULL COMMENT '活动 ID',
    tag         VARCHAR(50) NOT NULL COMMENT '标签',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_tag_pair (activity_id, tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '活动标签';

-- 报名
CREATE TABLE signups (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id   BIGINT       NOT NULL COMMENT '活动 ID',
    student_id    BIGINT       NOT NULL COMMENT '学生 ID',
    remark        VARCHAR(500) NULL COMMENT '报名备注',
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING / APPROVED / REJECTED / CANCELLED',
    reviewer_id   BIGINT       NULL COMMENT '审核人 ID',
    review_reason VARCHAR(500) NULL COMMENT '审核原因',
    reviewed_at   DATETIME     NULL COMMENT '审核时间',
    cancelled_at  DATETIME     NULL COMMENT '撤销时间',
    created_at    DATETIME     NULL COMMENT '创建时间',
    updated_at    DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_signup_pair (activity_id, student_id),
    KEY idx_signup_activity_status (activity_id, status, created_at),
    KEY idx_signup_student (student_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报名';

-- 签到
CREATE TABLE signins (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT      NOT NULL COMMENT '活动 ID',
    signup_id   BIGINT      NOT NULL COMMENT '报名 ID',
    signed_by   BIGINT      NULL COMMENT '登记人 ID',
    signed_at   DATETIME    NULL COMMENT '签到时间',
    source      VARCHAR(20) NULL COMMENT 'NORMAL / MANUAL',
    remark      VARCHAR(500) NULL COMMENT '补签原因',
    created_at  DATETIME    NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_signin_signup (signup_id),
    KEY idx_signin_activity (activity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '签到';

-- 补签申请（学生发起，负责人审核）
CREATE TABLE signin_applications (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id   BIGINT       NOT NULL COMMENT '活动 ID',
    signup_id     BIGINT       NOT NULL COMMENT '报名 ID',
    student_id    BIGINT       NOT NULL COMMENT '学生 ID',
    signed_at     DATETIME     NOT NULL COMMENT '申请补签时间（晚于活动开始、不晚于活动结束且早于当前）',
    reason        VARCHAR(500) NOT NULL COMMENT '补签原因',
    status        VARCHAR(20)  NOT NULL COMMENT 'PENDING / APPROVED / REJECTED',
    reviewer_id   BIGINT       NULL COMMENT '审核人 ID',
    review_reason VARCHAR(500) NULL COMMENT '审核原因',
    reviewed_at   DATETIME     NULL COMMENT '审核时间',
    created_at    DATETIME     NULL COMMENT '提交时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_signin_application_signup (signup_id),
    KEY idx_signin_application_activity_status (activity_id, status, created_at),
    KEY idx_signin_application_student (student_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '补签申请';

-- 归档资料
CREATE TABLE archives (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT        NOT NULL COMMENT '活动 ID',
    uploader_id BIGINT        NULL COMMENT '上传人 ID',
    file_id     BIGINT        NOT NULL COMMENT '文件对象 ID',
    summary     VARCHAR(1000) NOT NULL COMMENT '活动总结',
    visibility  VARCHAR(20)   NOT NULL COMMENT 'PUBLIC / CLUB_ONLY / PRIVATE',
    version     INT           NOT NULL DEFAULT 1 COMMENT '归档版本（按活动递增）',
    created_at  DATETIME      NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_archive_activity_version (activity_id, version),
    KEY idx_archive_activity (activity_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '归档资料';

-- 站内消息
CREATE TABLE messages (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    recipient_id BIGINT        NOT NULL COMMENT '接收人 ID',
    msg_type     VARCHAR(30)   NULL COMMENT '消息类型',
    title        VARCHAR(150)  NULL COMMENT '标题',
    content      VARCHAR(2000) NULL COMMENT '内容',
    is_read      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已读',
    created_at   DATETIME      NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_message_recipient_read (recipient_id, is_read, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息';

-- 审计日志
CREATE TABLE audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id      VARCHAR(64)  NULL COMMENT '链路标识',
    operator_id   BIGINT       NULL COMMENT '操作者 ID',
    resource_type VARCHAR(50)  NULL COMMENT '资源类型',
    resource_id   BIGINT       NULL COMMENT '资源 ID',
    action        VARCHAR(50)  NULL COMMENT '操作动作',
    before_json   TEXT         NULL COMMENT '变更前快照',
    after_json    TEXT         NULL COMMENT '变更后快照',
    result_mark   VARCHAR(20)  NULL COMMENT '操作结果',
    created_at    DATETIME     NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_audit_resource (resource_type, resource_id, created_at),
    KEY idx_audit_operator (operator_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计日志';

-- 文件对象
CREATE TABLE file_objects (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    object_key    VARCHAR(255) NOT NULL COMMENT '系统生成的对象键',
    original_name VARCHAR(255) NULL COMMENT '原始文件名（仅展示）',
    mime_type     VARCHAR(100) NULL COMMENT 'MIME 类型',
    size_bytes    BIGINT       NULL COMMENT '字节大小',
    sha256        VARCHAR(64)  NULL COMMENT '内容摘要',
    created_at    DATETIME     NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_key (object_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件对象';

-- 消息投递幂等表
CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_key    VARCHAR(120) NOT NULL COMMENT '业务事件唯一键',
    recipient_id BIGINT       NULL COMMENT '接收人 ID',
    channel      VARCHAR(20)  NULL COMMENT '投递渠道',
    status       VARCHAR(20)  NULL COMMENT 'PENDING / SENT / FAILED',
    created_at   DATETIME     NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_event (event_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消息投递幂等表';

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 初始化语句（参考，由应用启动的 DataInitializer 执行，勿手工插入明文密码）
-- INSERT INTO roles (code, name, created_at) VALUES
--   ('STUDENT', '学生', NOW()),
--   ('CLUB_ADMIN', '社团管理员', NOW()),
--   ('PLATFORM_ADMIN', '平台管理员', NOW());
--
-- 演示账号（密码均为 123456，BCrypt 摘要由 DataInitializer 生成）
-- INSERT INTO user_account (username, password_hash, real_name, student_no, status, created_at, updated_at)
--   VALUES ('admin', '<BCrypt>', '平台管理员', 'ADMIN001', 'ACTIVE', NOW(), NOW());
-- INSERT INTO user_roles (user_id, role_id, created_at)
--   SELECT u.id, r.id, NOW() FROM user_account u, roles r
--   WHERE u.username = 'admin' AND r.code = 'PLATFORM_ADMIN';
-- ------------------------------------------------------------
