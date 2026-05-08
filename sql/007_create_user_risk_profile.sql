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

    register_base_score INT NOT NULL DEFAULT 0,

    current_env_score INT NOT NULL DEFAULT 0,

    behavior_score_delta INT NOT NULL DEFAULT 0,

    lock_count INT NOT NULL DEFAULT 0,

    last_locked_at TIMESTAMPTZ,

    lock_until TIMESTAMPTZ,

    lock_reason VARCHAR(128),

    risk_recovery_started_at TIMESTAMPTZ,

    last_risk_penalty_at TIMESTAMPTZ,

    -- 当前风险分
    current_score INT NOT NULL DEFAULT 0,

    -- 风险等级：L1 / L2 / L3 / L4 / L5
    risk_level VARCHAR(16) NOT NULL DEFAULT 'L1',

    -- 最近一次登录时间
    last_login_at TIMESTAMPTZ,

    -- 最近一次登录 IP
    last_login_ip VARCHAR(64),

    -- 最近一次登录设备指纹
    last_device_fingerprint TEXT,

    -- 最近更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_risk_profile_risk_level
        CHECK (risk_level IN ('L1', 'L2', 'L3', 'L4', 'L5', 'L6'))
);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_current_score
    ON user_risk_profile (current_score);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_risk_level
    ON user_risk_profile (risk_level);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_last_login_at
    ON user_risk_profile (last_login_at);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_last_login_ip
    ON user_risk_profile (last_login_ip);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_lock_until
    ON user_risk_profile (lock_until);

CREATE INDEX IF NOT EXISTS idx_user_risk_profile_recovery
    ON user_risk_profile (lock_count, risk_recovery_started_at);

COMMENT ON COLUMN user_risk_profile.register_base_score IS '注册成功时的初始基础分，只写一次，作为账号冷启动分';
COMMENT ON COLUMN user_risk_profile.current_env_score IS '当前环境分，即当前 IP 分和设备分合成后的分数';
COMMENT ON COLUMN user_risk_profile.behavior_score_delta IS '行为修正分，给 current_score 加上一个正数或负数';
COMMENT ON COLUMN user_risk_profile.current_score IS '实际生效总分，最终用于映射 risk_level';
COMMENT ON COLUMN user_risk_profile.lock_count IS '账号累计被锁定次数';
COMMENT ON COLUMN user_risk_profile.last_locked_at IS '最近一次被锁定时间';
COMMENT ON COLUMN user_risk_profile.lock_until IS '账号锁定截止时间，为空或早于当前时间表示未锁定';
COMMENT ON COLUMN user_risk_profile.lock_reason IS '最近一次锁定原因';
COMMENT ON COLUMN user_risk_profile.risk_recovery_started_at IS '账号登录解封后，风险恢复观察期开始时间；未登录解封则为空';
COMMENT ON COLUMN user_risk_profile.last_risk_penalty_at IS '最近一次触发账号分控扣分或锁定的时间';

COMMENT ON TABLE user_risk_profile IS '用户当前风险画像表：一行代表一个用户当前的风险状态';
COMMENT ON COLUMN user_risk_profile.user_id IS '业务用户 ID，直接作为主键，不设置物理外键';
COMMENT ON COLUMN user_risk_profile.current_score IS '当前风险分';
COMMENT ON COLUMN user_risk_profile.risk_level IS '风险等级：L1 / L2 / L3 / L4 / L5';
COMMENT ON COLUMN user_risk_profile.last_login_at IS '最近一次登录时间';
COMMENT ON COLUMN user_risk_profile.last_login_ip IS '最近一次登录 IP';
COMMENT ON COLUMN user_risk_profile.last_device_fingerprint IS '最近一次登录设备指纹';
COMMENT ON COLUMN user_risk_profile.updated_at IS '最近更新时间';
COMMENT ON COLUMN user_risk_profile.current_score IS '实际生效总分，最终用于映射 risk_level';
