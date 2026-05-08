-- ============================================
-- 文件名：010_create_user_risk_score_event.sql
-- 说明：用户风险分变更流水表
-- 约定：
-- 1. 一行代表一次账号风险分变更事件；
-- 2. 只记录分数变化原因和变化前后状态，不存当前状态；
-- 3. 当前状态仍然以 user_risk_profile 为准；
-- 4. 不设置物理外键，user_id 直接保存业务用户 ID；
-- 5. id 使用业务侧雪花 ID 生成，不使用数据库自增。
-- ============================================

CREATE TABLE IF NOT EXISTS user_risk_score_event (
    -- 流水 ID，由业务侧雪花 ID 生成
    id BIGINT PRIMARY KEY,

    -- 业务用户 ID，不设置物理外键
    user_id BIGINT NOT NULL,

    -- 风险事件类型，例如 PWD_FAIL_30M / AUTH_PRESSURE_30M / IP_SWITCH_24H
    event_type VARCHAR(128) NOT NULL,

    -- 本次变更前的账号实际分
    score_before INT NOT NULL,

    -- 本次分数变化值，可以是正数，也可以是负数
    score_delta INT NOT NULL,

    -- 本次变更后的账号实际分
    score_after INT NOT NULL,

    -- 本次变更前的风险等级
    risk_level_before VARCHAR(16),

    -- 本次变更后的风险等级
    risk_level_after VARCHAR(16),

    -- 简短原因，方便人工排查
    reason VARCHAR(128),

    -- 触发本次事件时的客户端 IP
    ip VARCHAR(64),

    -- 触发本次事件时的设备指纹
    device_fingerprint TEXT,

    -- 扩展信息，例如窗口计数、命中的阈值、Redis key 等
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 事件创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_risk_score_event_score_before
        CHECK (score_before >= 0),

    CONSTRAINT ck_user_risk_score_event_score_after
        CHECK (score_after >= 0),

    CONSTRAINT ck_user_risk_score_event_level_before
        CHECK (risk_level_before IS NULL OR risk_level_before IN ('L1', 'L2', 'L3', 'L4', 'L5', 'L6')),

    CONSTRAINT ck_user_risk_score_event_level_after
        CHECK (risk_level_after IS NULL OR risk_level_after IN ('L1', 'L2', 'L3', 'L4', 'L5', 'L6'))
);

CREATE INDEX IF NOT EXISTS idx_user_risk_score_event_user_created_at
    ON user_risk_score_event (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_risk_score_event_event_type
    ON user_risk_score_event (event_type);

CREATE INDEX IF NOT EXISTS idx_user_risk_score_event_created_at
    ON user_risk_score_event (created_at);

COMMENT ON TABLE user_risk_score_event IS '用户风险分变更流水表：记录账号风险分每一次变化的原因和变化前后状态';
COMMENT ON COLUMN user_risk_score_event.id IS '流水 ID，由业务侧雪花 ID 生成';
COMMENT ON COLUMN user_risk_score_event.user_id IS '业务用户 ID，不设置物理外键';
COMMENT ON COLUMN user_risk_score_event.event_type IS '风险事件类型，例如 PWD_FAIL_30M / AUTH_PRESSURE_30M / IP_SWITCH_24H';
COMMENT ON COLUMN user_risk_score_event.score_before IS '本次变更前的账号实际分';
COMMENT ON COLUMN user_risk_score_event.score_delta IS '本次分数变化值，可以是正数，也可以是负数';
COMMENT ON COLUMN user_risk_score_event.score_after IS '本次变更后的账号实际分';
COMMENT ON COLUMN user_risk_score_event.risk_level_before IS '本次变更前的风险等级';
COMMENT ON COLUMN user_risk_score_event.risk_level_after IS '本次变更后的风险等级';
COMMENT ON COLUMN user_risk_score_event.reason IS '简短原因，方便人工排查';
COMMENT ON COLUMN user_risk_score_event.ip IS '触发本次事件时的客户端 IP';
COMMENT ON COLUMN user_risk_score_event.device_fingerprint IS '触发本次事件时的设备指纹';
COMMENT ON COLUMN user_risk_score_event.metadata IS '扩展信息，例如窗口计数、命中的阈值、Redis key 等';
COMMENT ON COLUMN user_risk_score_event.created_at IS '事件创建时间';
