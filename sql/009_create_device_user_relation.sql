-- ============================================
-- 文件名：009_create_device_user_relation.sql
-- 说明：设备与账号关系表
-- 约定：
-- 1. 一行表示一个设备与一个账号之间的关系
-- 2. 不设置物理外键
-- 3. 主键使用应用层生成的 16 byte ID
-- ============================================

CREATE TABLE IF NOT EXISTS device_user_relation (
    -- 主键：应用层生成的 16 byte ID
    id BYTEA PRIMARY KEY,

    -- 设备指纹
    device_fingerprint VARCHAR(255) NOT NULL,

    -- 业务用户 ID
    user_id BIGINT NOT NULL,

    -- 第一次观察到该设备与该账号关联的时间
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次观察到该设备与该账号关联的时间
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 该设备与该账号成功登录次数
    success_count INT NOT NULL DEFAULT 0,

    -- 该设备与该账号失败登录次数
    fail_count INT NOT NULL DEFAULT 0,

    CONSTRAINT ck_device_user_relation_id_16_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT uq_device_user_relation_device_user
        UNIQUE (device_fingerprint, user_id)
);

CREATE INDEX IF NOT EXISTS idx_device_user_relation_device_fingerprint
    ON device_user_relation (device_fingerprint);

CREATE INDEX IF NOT EXISTS idx_device_user_relation_user_id
    ON device_user_relation (user_id);

CREATE INDEX IF NOT EXISTS idx_device_user_relation_last_seen_at
    ON device_user_relation (last_seen_at);

COMMENT ON TABLE device_user_relation IS '设备与账号关系表：记录设备与账号之间的关联关系';
COMMENT ON COLUMN device_user_relation.id IS '主键，16 byte，应用层使用 HybridSemaphoreIdWorker 生成';
COMMENT ON COLUMN device_user_relation.device_fingerprint IS '设备指纹';
COMMENT ON COLUMN device_user_relation.user_id IS '业务用户 ID，不设置物理外键';
COMMENT ON COLUMN device_user_relation.first_seen_at IS '第一次观察到设备与账号关联的时间';
COMMENT ON COLUMN device_user_relation.last_seen_at IS '最近一次观察到设备与账号关联的时间';
COMMENT ON COLUMN device_user_relation.success_count IS '该设备与该账号成功登录次数';
COMMENT ON COLUMN device_user_relation.fail_count IS '该设备与该账号失败登录次数';
