-- ============================================
-- 文件名：009_create_device_user_relation.sql
-- 说明：记录设备风险画像与用户账号之间的关联关系
-- ============================================

CREATE TABLE IF NOT EXISTS device_user_relation (
    -- 16 字节主键，由应用代码中的 HybridSemaphoreIdWorker 生成。
    id BYTEA PRIMARY KEY,

    -- 逻辑关联 device_risk_profile.id；避免使用较长的原始设备指纹作为关系键。
    device_id BYTEA NOT NULL,

    -- 业务用户 ID。
    user_id BIGINT NOT NULL,

    -- 首次识别到该设备与该用户关联的时间。
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次识别到该设备与该用户关联的时间。
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 该设备与该用户关联下的成功安全操作次数，包括注册成功、登录成功、手机号登录成功、密码重置成功。
    success_count INT NOT NULL DEFAULT 0,

    -- 该设备与该用户关联下的失败或被风控拦截次数。
    fail_count INT NOT NULL DEFAULT 0,

    CONSTRAINT ck_device_user_relation_id_16_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_device_user_relation_device_id_16_bytes
        CHECK (octet_length(device_id) = 16),

    CONSTRAINT uq_device_user_relation_device_user
        UNIQUE (device_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_device_user_relation_device_id
    ON device_user_relation (device_id);

CREATE INDEX IF NOT EXISTS idx_device_user_relation_user_id
    ON device_user_relation (user_id);

CREATE INDEX IF NOT EXISTS idx_device_user_relation_last_seen_at
    ON device_user_relation (last_seen_at);

COMMENT ON TABLE device_user_relation IS '设备与用户账号关联关系表';
COMMENT ON COLUMN device_user_relation.id IS '16 字节主键，由 HybridSemaphoreIdWorker 生成';
COMMENT ON COLUMN device_user_relation.device_id IS '逻辑关联的设备风险画像 ID，对应 device_risk_profile.id';
COMMENT ON COLUMN device_user_relation.user_id IS '业务用户 ID';
COMMENT ON COLUMN device_user_relation.first_seen_at IS '首次识别到该设备与该用户关联的时间';
COMMENT ON COLUMN device_user_relation.last_seen_at IS '最近一次识别到该设备与该用户关联的时间';
COMMENT ON COLUMN device_user_relation.success_count IS '该设备与该用户关联下的成功安全操作次数，包括注册成功、登录成功、手机号登录成功、密码重置成功';
COMMENT ON COLUMN device_user_relation.fail_count IS '该设备与该用户关联下的失败或被风控拦截次数';
