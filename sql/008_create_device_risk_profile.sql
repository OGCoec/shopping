-- ============================================
-- 文件名：008_create_device_risk_profile.sql
-- 说明：设备当前风险画像表
-- 约定：
-- 1. 一行代表一个设备当前的风险状态
-- 2. 不记录历史明细，历史明细看登录成功/失败记录与设备账号关系表
-- 3. 不设置物理外键
-- 4. 初始分默认 6000
-- ============================================

CREATE TABLE IF NOT EXISTS device_risk_profile (
    -- 设备指纹直接作为主键
    device_fingerprint VARCHAR(255) PRIMARY KEY,

    -- 当前风险分，默认 6000
    current_score INT NOT NULL DEFAULT 6000,

    -- 风险等级：L1 / L2 / L3 / L4 / L5 / L6
    risk_level VARCHAR(16) NOT NULL DEFAULT 'L3',

    -- 首次出现时间
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次出现时间
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次登录 IP
    last_login_ip VARCHAR(64),

    -- 关联过的账号数量（可由程序维护，也可定时统计回写）
    linked_user_count INT NOT NULL DEFAULT 0,

    -- 最近时间窗口内不同 IP 数
    recent_distinct_ip_count INT NOT NULL DEFAULT 0,

    -- 最近时间窗口内 IP 切换次数
    recent_ip_switch_count INT NOT NULL DEFAULT 0,

    -- 最近更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_device_risk_profile_risk_level
        CHECK (risk_level IN ('L1', 'L2', 'L3', 'L4', 'L5', 'L6'))
);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_current_score
    ON device_risk_profile (current_score);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_risk_level
    ON device_risk_profile (risk_level);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_last_seen_at
    ON device_risk_profile (last_seen_at);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_last_login_ip
    ON device_risk_profile (last_login_ip);

COMMENT ON TABLE device_risk_profile IS '设备当前风险画像表：一行代表一个设备当前的风险状态';
COMMENT ON COLUMN device_risk_profile.device_fingerprint IS '设备指纹，直接作为主键';
COMMENT ON COLUMN device_risk_profile.current_score IS '当前风险分，默认初始值为 6000';
COMMENT ON COLUMN device_risk_profile.risk_level IS '风险等级：L1 / L2 / L3 / L4 / L5 / L6';
COMMENT ON COLUMN device_risk_profile.first_seen_at IS '设备首次出现时间';
COMMENT ON COLUMN device_risk_profile.last_seen_at IS '设备最近一次出现时间';
COMMENT ON COLUMN device_risk_profile.last_login_ip IS '设备最近一次登录 IP';
COMMENT ON COLUMN device_risk_profile.linked_user_count IS '设备关联过的账号数量';
COMMENT ON COLUMN device_risk_profile.recent_distinct_ip_count IS '最近时间窗口内不同 IP 数';
COMMENT ON COLUMN device_risk_profile.recent_ip_switch_count IS '最近时间窗口内 IP 切换次数';
COMMENT ON COLUMN device_risk_profile.updated_at IS '最近更新时间';
