-- ============================================
-- 文件名：011_view_device_risk_score_event.sql
-- 说明：device_risk_score_event 可读视图，将 16 字节 BYTEA 列 device_id 转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；id 为 BIGSERIAL，原样保留
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_device_risk_score_event AS
SELECT
    id,                                            -- 流水 ID，BIGSERIAL，非 BYTEA
    to_base62(device_id)     AS device_id_base62,  -- 关联 device_risk_profile.id 的 Base62
    encode(device_id, 'hex') AS device_id_hex,     -- 兜底 Hex 展示
    score_before,
    penalty_score,
    score_after,
    reason,
    created_at
FROM device_risk_score_event;

COMMENT ON VIEW v_device_risk_score_event IS '设备风险扣分流水可读视图：device_id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_device_risk_score_event.id IS '流水 ID，由数据库自增生成';
COMMENT ON COLUMN v_device_risk_score_event.device_id_base62 IS '设备 ID 的 Base62 形式，对应 device_risk_profile.id';
COMMENT ON COLUMN v_device_risk_score_event.device_id_hex IS '设备 ID 的 Hex 形式';
COMMENT ON COLUMN v_device_risk_score_event.score_before IS '本次扣分前的设备风险分';
COMMENT ON COLUMN v_device_risk_score_event.penalty_score IS '本次扣分分值，保存正数';
COMMENT ON COLUMN v_device_risk_score_event.score_after IS '本次扣分后的设备风险分';
COMMENT ON COLUMN v_device_risk_score_event.reason IS '扣分原因，例如 IP_CHANGED / IMPOSSIBLE_TRAVEL / LINKED_USER_COUNT_5';
COMMENT ON COLUMN v_device_risk_score_event.created_at IS '事件创建时间';