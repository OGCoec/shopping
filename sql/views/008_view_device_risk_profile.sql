-- ============================================
-- 文件名：008_view_device_risk_profile.sql
-- 说明：device_risk_profile 可读视图，将 16 字节 BYTEA 主键转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；原表读写仍走 BYTEA 主键
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_device_risk_profile AS
SELECT
    to_base62(id)     AS id_base62,   -- 与后端一致的 Base62 主键
    encode(id, 'hex') AS id_hex,      -- 兜底 Hex 展示
    device_fingerprint,
    current_score,
    risk_level,
    first_seen_at,
    last_seen_at,
    last_login_ip,
    last_ip_seen_at,
    last_penalized_ip_transition,
    last_penalty_at,
    last_penalty_score,
    last_penalty_reason,
    used_ip_list,
    linked_user_count,
    linked_user_penalty_tier,
    last_linked_user_penalty_at,
    last_linked_user_penalty_score,
    last_linked_user_penalty_reason,
    recent_distinct_ip_count,
    recent_ip_switch_count,
    updated_at
FROM device_risk_profile;

COMMENT ON VIEW v_device_risk_profile IS '设备风险画像可读视图：主键 id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_device_risk_profile.id_base62 IS '主键 id 的 Base62 可读形式，与后端 HybridIdCodec 一致';
COMMENT ON COLUMN v_device_risk_profile.id_hex IS '主键 id 的 Hex 可读形式';
COMMENT ON COLUMN v_device_risk_profile.device_fingerprint IS '浏览器原始设备指纹，作为设备画像的唯一业务键';
COMMENT ON COLUMN v_device_risk_profile.current_score IS '当前设备风险分，默认 6000，分数越高表示风险越低';
COMMENT ON COLUMN v_device_risk_profile.risk_level IS '当前设备风险等级，取值 L1 / L2 / L3 / L4 / L5 / L6';
COMMENT ON COLUMN v_device_risk_profile.first_seen_at IS '首次识别到该设备的时间';
COMMENT ON COLUMN v_device_risk_profile.last_seen_at IS '最近一次识别到该设备的时间';
COMMENT ON COLUMN v_device_risk_profile.last_login_ip IS '最近一次登录或注册使用的 IP';
COMMENT ON COLUMN v_device_risk_profile.last_ip_seen_at IS 'last_login_ip 的记录时间，用于长效 IP 变化速度计算';
COMMENT ON COLUMN v_device_risk_profile.last_penalized_ip_transition IS '最近一次已扣分的 IP 变化链路，用于避免重复扣分';
COMMENT ON COLUMN v_device_risk_profile.last_penalty_at IS '最近一次 IP 变化导致设备扣分的时间';
COMMENT ON COLUMN v_device_risk_profile.last_penalty_score IS '最近一次 IP 变化导致设备扣分的分值';
COMMENT ON COLUMN v_device_risk_profile.last_penalty_reason IS '最近一次 IP 变化导致设备扣分的原因';
COMMENT ON COLUMN v_device_risk_profile.used_ip_list IS '该设备使用过的全部 IP 集合，使用 JSONB 数组存储';
COMMENT ON COLUMN v_device_risk_profile.linked_user_count IS '该设备关联过的用户账户数量';
COMMENT ON COLUMN v_device_risk_profile.linked_user_penalty_tier IS '关联用户数量对应的扣分档位';
COMMENT ON COLUMN v_device_risk_profile.last_linked_user_penalty_at IS '最近一次因关联用户数量扣分的时间';
COMMENT ON COLUMN v_device_risk_profile.last_linked_user_penalty_score IS '最近一次因关联用户数量扣分的分值';
COMMENT ON COLUMN v_device_risk_profile.last_linked_user_penalty_reason IS '最近一次因关联用户数量扣分的原因';
COMMENT ON COLUMN v_device_risk_profile.recent_distinct_ip_count IS '近期使用过的不同 IP 数量';
COMMENT ON COLUMN v_device_risk_profile.recent_ip_switch_count IS '近期 IP 切换次数';
COMMENT ON COLUMN v_device_risk_profile.updated_at IS '风险画像最近更新时间';