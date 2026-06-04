-- ============================================
-- 文件名：009_view_device_user_relation.sql
-- 说明：device_user_relation 可读视图，将 16 字节 BYTEA 列转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；原表读写仍走 BYTEA 列
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_device_user_relation AS
SELECT
    to_base62(id)            AS id_base62,         -- 与后端一致的 Base62 主键
    encode(id, 'hex')        AS id_hex,            -- 兜底 Hex 展示
    to_base62(device_id)     AS device_id_base62,  -- 关联 device_risk_profile.id 的 Base62
    encode(device_id, 'hex') AS device_id_hex,     -- 兜底 Hex 展示
    user_id,
    first_seen_at,
    last_seen_at,
    success_count,
    fail_count
FROM device_user_relation;

COMMENT ON VIEW v_device_user_relation IS '设备与用户关联关系可读视图：id、device_id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_device_user_relation.id_base62 IS '主键 id 的 Base62 可读形式，与后端 HybridIdCodec 一致';
COMMENT ON COLUMN v_device_user_relation.id_hex IS '主键 id 的 Hex 可读形式';
COMMENT ON COLUMN v_device_user_relation.device_id_base62 IS '关联设备风险画像 ID 的 Base62 形式，对应 device_risk_profile.id';
COMMENT ON COLUMN v_device_user_relation.device_id_hex IS '关联设备风险画像 ID 的 Hex 形式';
COMMENT ON COLUMN v_device_user_relation.user_id IS '业务用户 ID';
COMMENT ON COLUMN v_device_user_relation.first_seen_at IS '首次识别到该设备与该用户关联的时间';
COMMENT ON COLUMN v_device_user_relation.last_seen_at IS '最近一次识别到该设备与该用户关联的时间';
COMMENT ON COLUMN v_device_user_relation.success_count IS '该设备与该用户关联下的成功安全操作次数，包括注册成功、登录成功、手机号登录成功、密码重置成功';
COMMENT ON COLUMN v_device_user_relation.fail_count IS '该设备与该用户关联下的失败或被风控拦截次数';