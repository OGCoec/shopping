-- ============================================
-- 文件名：014_create_user_account_self_deletion.sql
-- 说明：用户主动注销账号拦截表
-- 约定：
-- 1. 一行代表一个用户主动注销的账号；
-- 2. 结构与风控强制注销账号拦截表基本一致；
-- 3. 多记录是否已经确认注销，以及用户填写的注销理由；
-- 4. 不设置物理外键，user_id 直接保存业务用户 ID。
-- ============================================

CREATE TABLE IF NOT EXISTS user_account_self_deletion (
    -- 注销记录 ID，由业务侧雪花 ID 生成
    id BIGINT PRIMARY KEY,

    -- 业务用户 ID，不设置物理外键
    user_id BIGINT NOT NULL,

    -- 主动注销账号的邮箱
    email VARCHAR(320) NOT NULL,

    -- 主动注销账号的邮箱哈希，用于注册和登录拦截查询
    email_hash VARCHAR(128) NOT NULL,

    -- 主动注销账号绑定的手机号，账号未绑定手机号时为空
    phone VARCHAR(32),

    -- 主动注销账号绑定手机号的哈希，用于手机号注册和登录拦截查询
    phone_hash VARCHAR(128),

    -- 是否已经确认注销
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- 用户填写的注销理由
    deletion_reason TEXT,

    -- 注销时间
    deleted_at TIMESTAMPTZ,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_account_self_deletion_reason_required
        CHECK (is_deleted = FALSE OR deletion_reason IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_account_self_deletion_email_hash
    ON user_account_self_deletion (email_hash);

CREATE INDEX IF NOT EXISTS idx_user_account_self_deletion_phone_hash
    ON user_account_self_deletion (phone_hash)
    WHERE phone_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_account_self_deletion_user_id
    ON user_account_self_deletion (user_id);

CREATE INDEX IF NOT EXISTS idx_user_account_self_deletion_deleted_at
    ON user_account_self_deletion (deleted_at);

COMMENT ON TABLE user_account_self_deletion IS '用户主动注销账号拦截表';
COMMENT ON COLUMN user_account_self_deletion.id IS '注销记录 ID，由业务侧雪花 ID 生成';
COMMENT ON COLUMN user_account_self_deletion.user_id IS '业务用户 ID，不设置物理外键';
COMMENT ON COLUMN user_account_self_deletion.email IS '主动注销账号的邮箱';
COMMENT ON COLUMN user_account_self_deletion.email_hash IS '主动注销账号的邮箱哈希，用于注册和登录拦截查询';
COMMENT ON COLUMN user_account_self_deletion.phone IS '主动注销账号绑定的手机号，账号未绑定手机号时为空';
COMMENT ON COLUMN user_account_self_deletion.phone_hash IS '主动注销账号绑定手机号的哈希，用于手机号注册和登录拦截查询';
COMMENT ON COLUMN user_account_self_deletion.is_deleted IS '是否已经确认注销';
COMMENT ON COLUMN user_account_self_deletion.deletion_reason IS '用户填写的注销理由';
COMMENT ON COLUMN user_account_self_deletion.deleted_at IS '注销时间';
COMMENT ON COLUMN user_account_self_deletion.created_at IS '创建时间';
