-- ============================================
-- 文件名：008_create_device_risk_profile.sql
-- 说明：记录当前设备的风险画像
-- ============================================

CREATE TABLE IF NOT EXISTS device_risk_profile (
    -- 16 字节主键，由应用代码中的 HybridSemaphoreIdWorker 生成。
    id BYTEA PRIMARY KEY,

    -- 浏览器原始设备指纹；由于长度可能较长，不直接作为主键。
    device_fingerprint TEXT NOT NULL,

    -- 当前设备风险分，字段默认 6000；当前业务写入固定为 6666，分数越高表示风险越低。
    current_score INT NOT NULL DEFAULT 6000,

    -- 当前设备风险等级。
    risk_level VARCHAR(16) NOT NULL DEFAULT 'L3',

    -- 首次识别到该设备的时间。
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次识别到该设备的时间。
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次登录或注册使用的 IP。
    last_login_ip VARCHAR(64),

    -- last_login_ip recorded time, used for long-term IP change speed checks.
    last_ip_seen_at TIMESTAMPTZ,

    -- Last penalized IP transition, for example: 1.1.1.1->2.2.2.2.
    last_penalized_ip_transition TEXT,

    -- Last IP-change device-score penalty metadata, used for audit and dedupe.
    last_penalty_at TIMESTAMPTZ,
    last_penalty_score INT NOT NULL DEFAULT 0,
    last_penalty_reason VARCHAR(128),

    -- 该设备使用过的全部 IP 集合，使用 JSONB 数组存储。
    used_ip_list JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 该设备关联过的用户账户数量。
    linked_user_count INT NOT NULL DEFAULT 0,

    -- 近期使用过的不同 IP 数量。
    recent_distinct_ip_count INT NOT NULL DEFAULT 0,

    -- 近期 IP 切换次数。
    recent_ip_switch_count INT NOT NULL DEFAULT 0,

    -- 风险画像最近更新时间。
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_device_risk_profile_id_16_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT uq_device_risk_profile_fingerprint
        UNIQUE (device_fingerprint),

    CONSTRAINT ck_device_risk_profile_risk_level
        CHECK (risk_level IN ('L1', 'L2', 'L3', 'L4', 'L5', 'L6'))
);

ALTER TABLE device_risk_profile
    ADD COLUMN IF NOT EXISTS last_ip_seen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_penalized_ip_transition TEXT,
    ADD COLUMN IF NOT EXISTS last_penalty_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_penalty_score INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_penalty_reason VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_current_score
    ON device_risk_profile (current_score);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_risk_level
    ON device_risk_profile (risk_level);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_last_seen_at
    ON device_risk_profile (last_seen_at);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_last_login_ip
    ON device_risk_profile (last_login_ip);

CREATE INDEX IF NOT EXISTS idx_device_risk_profile_last_ip_seen_at
    ON device_risk_profile (last_ip_seen_at);

COMMENT ON TABLE device_risk_profile IS '设备当前风险画像表';
COMMENT ON COLUMN device_risk_profile.id IS '16 字节主键，由 HybridSemaphoreIdWorker 生成';
COMMENT ON COLUMN device_risk_profile.device_fingerprint IS '浏览器原始设备指纹，作为设备画像的唯一业务键';
COMMENT ON COLUMN device_risk_profile.current_score IS '当前设备风险分，字段默认 6000；当前业务写入固定为 6666，分数越高表示风险越低';
COMMENT ON COLUMN device_risk_profile.risk_level IS '当前设备风险等级，取值 L1 / L2 / L3 / L4 / L5 / L6';
COMMENT ON COLUMN device_risk_profile.first_seen_at IS '首次识别到该设备的时间';
COMMENT ON COLUMN device_risk_profile.last_seen_at IS '最近一次识别到该设备的时间';
COMMENT ON COLUMN device_risk_profile.last_login_ip IS '最近一次登录或注册使用的 IP';
COMMENT ON COLUMN device_risk_profile.last_ip_seen_at IS 'last_login_ip 的记录时间，用于长效 IP 变化速度计算';
COMMENT ON COLUMN device_risk_profile.last_penalized_ip_transition IS '最近一次已扣分的 IP 变化链路，用于避免重复扣分';
COMMENT ON COLUMN device_risk_profile.last_penalty_at IS '最近一次 IP 变化导致设备分扣分的时间';
COMMENT ON COLUMN device_risk_profile.last_penalty_score IS '最近一次 IP 变化导致设备分扣分的分值';
COMMENT ON COLUMN device_risk_profile.last_penalty_reason IS '最近一次 IP 变化导致设备分扣分的原因';
COMMENT ON COLUMN device_risk_profile.used_ip_list IS '该设备使用过的全部 IP 集合，使用 JSONB 数组存储';
COMMENT ON COLUMN device_risk_profile.linked_user_count IS '该设备关联过的用户账户数量';
COMMENT ON COLUMN device_risk_profile.recent_distinct_ip_count IS '近期使用过的不同 IP 数量';
COMMENT ON COLUMN device_risk_profile.recent_ip_switch_count IS '近期 IP 切换次数';
COMMENT ON COLUMN device_risk_profile.updated_at IS '风险画像最近更新时间';
