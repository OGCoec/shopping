-- ============================================
-- 文件名：013_create_user_risk_account_termination.sql
-- 说明：风控强制注销账号拦截表
-- 约定：
-- 1. 一行代表一个因为风控原因被强制注销的账号；
-- 2. 进入本表的邮箱默认不允许再次注册，也不允许登录；
-- 3. 如果账号存在已绑定手机号，则手机号也可以用于后续注册/登录拦截；
-- 4. 不设置物理外键，user_id 直接保存业务用户 ID。
-- ============================================

CREATE TABLE IF NOT EXISTS user_risk_account_termination (
    -- 注销记录 ID，由业务侧雪花 ID 生成
    id BIGINT PRIMARY KEY,

    -- 业务用户 ID，不设置物理外键
    user_id BIGINT NOT NULL,

    -- 被强制注销账号的邮箱
    email VARCHAR(320) NOT NULL,

    -- 被强制注销账号的邮箱哈希，用于注册和登录拦截查询
    email_hash VARCHAR(128) NOT NULL,

    -- 被强制注销账号绑定的手机号，账号未绑定手机号时为空
    phone VARCHAR(32),

    -- 被强制注销账号绑定手机号的哈希，用于手机号注册和登录拦截查询
    phone_hash VARCHAR(128),

    -- 注销理由
    termination_reason VARCHAR(128) NOT NULL,

    -- 注销时间
    terminated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_risk_account_termination_email_hash
    ON user_risk_account_termination (email_hash);

CREATE INDEX IF NOT EXISTS idx_user_risk_account_termination_phone_hash
    ON user_risk_account_termination (phone_hash)
    WHERE phone_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_risk_account_termination_user_id
    ON user_risk_account_termination (user_id);

CREATE INDEX IF NOT EXISTS idx_user_risk_account_termination_terminated_at
    ON user_risk_account_termination (terminated_at);

COMMENT ON TABLE user_risk_account_termination IS '风控强制注销账号拦截表';
COMMENT ON COLUMN user_risk_account_termination.id IS '注销记录 ID，由业务侧雪花 ID 生成';
COMMENT ON COLUMN user_risk_account_termination.user_id IS '业务用户 ID，不设置物理外键';
COMMENT ON COLUMN user_risk_account_termination.email IS '被强制注销账号的邮箱';
COMMENT ON COLUMN user_risk_account_termination.email_hash IS '被强制注销账号的邮箱哈希，用于注册和登录拦截查询';
COMMENT ON COLUMN user_risk_account_termination.phone IS '被强制注销账号绑定的手机号，账号未绑定手机号时为空';
COMMENT ON COLUMN user_risk_account_termination.phone_hash IS '被强制注销账号绑定手机号的哈希，用于手机号注册和登录拦截查询';
COMMENT ON COLUMN user_risk_account_termination.termination_reason IS '注销理由';
COMMENT ON COLUMN user_risk_account_termination.terminated_at IS '注销时间';
COMMENT ON COLUMN user_risk_account_termination.created_at IS '创建时间';
