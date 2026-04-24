-- ============================================
-- 文件名：007_create_user_risk_profile.sql
-- 说明：用户当前风险画像表
-- 约定：
-- 1. 一行代表一个用户当前的风险状态
-- 2. 不记录历史明细，历史明细仍看登录成功/失败记录表
-- 3. 不设置物理外键
-- 4. 风险等级分为 5 级
-- ============================================

CREATE TABLE IF NOT EXISTS user_risk_profile (
    -- 直接使用业务用户 ID 作为主键
    user_id BIGINT PRIMARY KEY,

    -- 当前风险分
    current_score INT NOT NULL DEFAULT 0,

    -- 风险等级：L1 / L2 / L3 / L4 / L5
    risk_level VARCHAR(16) NOT NULL DEFAULT 'L1',

    -- 最近一次登录时间
    last_login_at TIMESTAMPTZ,

    -- 最近一次登录 IP
    last_login_ip VARCHAR(64),

    -- 最近一次登录设备指纹
    last_device_fingerprint VARCHAR(255),

    -- 最近更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_risk_profile_risk_level
        CHECK (risk_level IN ('L1', 'L2', 'L3', 'L4', 'L5'))
);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_current_score
    ON user_risk_profile (current_score);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_risk_level
    ON user_risk_profile (risk_level);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_last_login_at
    ON user_risk_profile (last_login_at);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_last_login_ip
    ON user_risk_profile (last_login_ip);

COMMENT ON TABLE user_risk_profile IS '用户当前风险画像表：一行代表一个用户当前的风险状态';
COMMENT ON COLUMN user_risk_profile.user_id IS '业务用户 ID，直接作为主键，不设置物理外键';
COMMENT ON COLUMN user_risk_profile.current_score IS '当前风险分';
COMMENT ON COLUMN user_risk_profile.risk_level IS '风险等级：L1 / L2 / L3 / L4 / L5';
COMMENT ON COLUMN user_risk_profile.last_login_at IS '最近一次登录时间';
COMMENT ON COLUMN user_risk_profile.last_login_ip IS '最近一次登录 IP';
COMMENT ON COLUMN user_risk_profile.last_device_fingerprint IS '最近一次登录设备指纹';
COMMENT ON COLUMN user_risk_profile.updated_at IS '最近更新时间';
