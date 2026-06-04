-- ============================================
-- 文件名：004_view_user_login_fail_record.sql
-- 说明：user_login_fail_record 可读视图，将 16 字节 BYTEA 主键转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；原表读写仍走 BYTEA 主键
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_user_login_fail_record AS
SELECT
    to_base62(id)     AS id_base62,   -- 与后端一致的 Base62 主键
    encode(id, 'hex') AS id_hex,      -- 兜底 Hex 展示
    user_id,
    login_identifier,
    login_type,
    fail_stage,
    fail_type,
    fail_message,
    login_ip,
    user_agent,
    device_fingerprint,
    extra_json,
    login_at
FROM user_login_fail_record;

COMMENT ON VIEW v_user_login_fail_record IS '用户登录失败记录可读视图：主键 id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_user_login_fail_record.id_base62 IS '主键 id 的 Base62 可读形式，与后端 HybridIdCodec 一致';
COMMENT ON COLUMN v_user_login_fail_record.id_hex IS '主键 id 的 Hex 可读形式';
COMMENT ON COLUMN v_user_login_fail_record.user_id IS '业务用户 ID；若尚未识别到用户可为空';
COMMENT ON COLUMN v_user_login_fail_record.login_identifier IS '用户本次输入的登录标识，例如邮箱、手机号、用户名';
COMMENT ON COLUMN v_user_login_fail_record.login_type IS '登录方式：EMAIL / PHONE / GOOGLE / GITHUB / MICROSOFT';
COMMENT ON COLUMN v_user_login_fail_record.fail_stage IS '失败发生阶段，例如 PASSWORD_CHECK / PHONE_VERIFY_CHECK / OAUTH_STATE_CHECK';
COMMENT ON COLUMN v_user_login_fail_record.fail_type IS '失败类型，例如 PASSWORD_INCORRECT / PHONE_VOIP_NOT_ALLOWED / OAUTH_STATE_INVALID';
COMMENT ON COLUMN v_user_login_fail_record.fail_message IS '失败说明，便于排查';
COMMENT ON COLUMN v_user_login_fail_record.login_ip IS '登录失败时的来源 IP';
COMMENT ON COLUMN v_user_login_fail_record.user_agent IS '浏览器或客户端标识';
COMMENT ON COLUMN v_user_login_fail_record.device_fingerprint IS '设备指纹，可为空';
COMMENT ON COLUMN v_user_login_fail_record.extra_json IS '扩展上下文，例如风控规则、手机号识别结果等';
COMMENT ON COLUMN v_user_login_fail_record.login_at IS '登录失败时间';