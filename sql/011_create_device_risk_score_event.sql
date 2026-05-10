-- ============================================
-- 文件名：011_create_device_risk_score_event.sql
-- 说明：设备风险扣分流水表
-- 约定：
-- 1. 一行代表一次设备风险分扣分事件；
-- 2. 只记录扣分原因和扣分前后状态，不存当前状态；
-- 3. 当前状态仍然以 device_risk_profile 为准；
-- 4. 不设置物理外键，device_id 直接保存 device_risk_profile.id；
-- 5. penalty_score 保存正数，例如扣 150 分就保存 150。
-- ============================================

CREATE TABLE IF NOT EXISTS device_risk_score_event (
    -- 流水 ID，由数据库自增生成
    id BIGSERIAL PRIMARY KEY,

    -- 设备 ID，对应 device_risk_profile.id，不设置物理外键
    device_id BYTEA NOT NULL,

    -- 本次扣分前的设备风险分
    score_before INT NOT NULL,

    -- 本次扣分分值，保存正数
    penalty_score INT NOT NULL,

    -- 本次扣分后的设备风险分
    score_after INT NOT NULL,

    -- 扣分原因，例如 IP_CHANGED / IMPOSSIBLE_TRAVEL / LINKED_USER_COUNT_5
    reason VARCHAR(128) NOT NULL,

    -- 事件创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_device_risk_score_event_device_id_16_bytes
        CHECK (octet_length(device_id) = 16),

    CONSTRAINT ck_device_risk_score_event_score_before
        CHECK (score_before >= 0 AND score_before <= 10000),

    CONSTRAINT ck_device_risk_score_event_penalty_score
        CHECK (penalty_score > 0 AND penalty_score <= 10000),

    CONSTRAINT ck_device_risk_score_event_score_after
        CHECK (score_after >= 0 AND score_after <= 10000),

    CONSTRAINT ck_device_risk_score_event_reason
        CHECK (btrim(reason) <> '')
);

CREATE INDEX IF NOT EXISTS idx_device_risk_score_event_device_created_at
    ON device_risk_score_event (device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_device_risk_score_event_reason
    ON device_risk_score_event (reason);

CREATE INDEX IF NOT EXISTS idx_device_risk_score_event_created_at
    ON device_risk_score_event (created_at);

COMMENT ON TABLE device_risk_score_event IS '设备风险扣分流水表：记录设备风险分每一次扣分的原因和扣分前后状态';
COMMENT ON COLUMN device_risk_score_event.id IS '流水 ID，由数据库自增生成';
COMMENT ON COLUMN device_risk_score_event.device_id IS '设备 ID，对应 device_risk_profile.id，不设置物理外键';
COMMENT ON COLUMN device_risk_score_event.score_before IS '本次扣分前的设备风险分';
COMMENT ON COLUMN device_risk_score_event.penalty_score IS '本次扣分分值，保存正数';
COMMENT ON COLUMN device_risk_score_event.score_after IS '本次扣分后的设备风险分';
COMMENT ON COLUMN device_risk_score_event.reason IS '扣分原因，例如 IP_CHANGED / IMPOSSIBLE_TRAVEL / LINKED_USER_COUNT_5';
COMMENT ON COLUMN device_risk_score_event.created_at IS '事件创建时间';
