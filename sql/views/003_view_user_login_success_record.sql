-- ============================================
-- 文件名：003_view_user_login_success_record.sql
-- 说明：user_login_success_record 可读视图，将 16 字节 BYTEA 主键转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；原表读写仍走 BYTEA 主键
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_user_login_success_record AS
SELECT
    to_base62(id)     AS id_base62,   -- 与后端一致的 Base62 主键
    encode(id, 'hex') AS id_hex,      -- 兜底 Hex 展示
    user_id,
    login_type,
    login_ip,
    user_agent,
    device_fingerprint,
    login_at
FROM user_login_success_record;

COMMENT ON VIEW v_user_login_success_record IS '用户登录成功记录可读视图：主键 id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_user_login_success_record.id_base62 IS '主键 id 的 Base62 可读形式，与后端 HybridIdCodec 一致';
COMMENT ON COLUMN v_user_login_success_record.id_hex IS '主键 id 的 Hex 可读形式';
COMMENT ON COLUMN v_user_login_success_record.user_id IS '业务用户 ID，不设置外键约束';
COMMENT ON COLUMN v_user_login_success_record.login_type IS '登录方式：EMAIL / PHONE / GOOGLE / GITHUB / MICROSOFT';
COMMENT ON COLUMN v_user_login_success_record.login_ip IS '登录成功时的来源 IP';
COMMENT ON COLUMN v_user_login_success_record.user_agent IS '浏览器或客户端标识';
COMMENT ON COLUMN v_user_login_success_record.device_fingerprint IS '设备指纹，可为空';
COMMENT ON COLUMN v_user_login_success_record.login_at IS '登录成功时间';