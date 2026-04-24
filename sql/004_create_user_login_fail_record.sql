-- ============================================
-- 文件名：004_create_user_login_fail_record.sql
-- 说明：用户登录失败记录表
-- 约定：仅在登录失败后写入
-- 主键：16 byte（二进制）
-- 不设置外键约束
-- 适配：PostgreSQL
-- ============================================

CREATE TABLE IF NOT EXISTS user_login_fail_record (
    -- 主键：应用层生成的 16 byte ID
    id BYTEA PRIMARY KEY,

    -- 业务用户 ID；若尚未识别到用户，可为空
    user_id BIGINT,

    -- 用户本次输入的登录标识，例如邮箱、手机号、用户名
    login_identifier VARCHAR(255),

    -- 登录方式：EMAIL / PHONE / GOOGLE / GITHUB / MICROSOFT
    login_type VARCHAR(32) NOT NULL,

    -- 失败发生阶段
    -- IDENTIFIER_CHECK：账号/邮箱/手机号检查阶段
    -- PASSWORD_CHECK：密码校验阶段
    -- OTP_CHECK：验证码校验阶段
    -- CHALLENGE_CHECK：验证码/挑战校验阶段
    -- PHONE_VERIFY_CHECK：补充手机号验证阶段
    -- OAUTH_STATE_CHECK：OAuth state/nonce 校验阶段
    -- ACCOUNT_STATUS_CHECK：账号状态检查阶段
    fail_stage VARCHAR(64) NOT NULL,

    -- 失败类型
    -- ACCOUNT_NOT_FOUND：账号不存在
    -- PASSWORD_INCORRECT：密码错误
    -- OTP_INCORRECT：验证码错误
    -- OTP_EXPIRED：验证码过期
    -- CAPTCHA_FAILED：挑战失败
    -- PHONE_REQUIRED：要求补充手机号
    -- PHONE_INVALID：手机号格式无效
    -- PHONE_VOIP_NOT_ALLOWED：虚拟号不允许
    -- PHONE_LANDLINE_NOT_ALLOWED：座机号不允许
    -- OAUTH_STATE_INVALID：OAuth state/nonce 无效
    -- ACCOUNT_LOCKED：账号锁定
    -- ACCOUNT_DISABLED：账号禁用
    -- RISK_BLOCKED：风控阻断
    fail_type VARCHAR(64) NOT NULL,

    -- 失败说明，便于排查
    fail_message VARCHAR(255),

    -- 登录失败时的来源 IP
    login_ip VARCHAR(64),

    -- 浏览器或客户端标识
    user_agent VARCHAR(512),

    -- 设备指纹，可为空
    device_fingerprint VARCHAR(255),

    -- 扩展上下文，例如风控规则、手机号识别结果等
    extra_json JSONB,

    -- 登录失败时间
    login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_login_fail_record_id_16_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_user_login_fail_record_login_type
        CHECK (login_type IN ('EMAIL', 'PHONE', 'GOOGLE', 'GITHUB', 'MICROSOFT')),

    CONSTRAINT ck_user_login_fail_record_fail_stage
        CHECK (fail_stage IN (
            'IDENTIFIER_CHECK',
            'PASSWORD_CHECK',
            'OTP_CHECK',
            'CHALLENGE_CHECK',
            'PHONE_VERIFY_CHECK',
            'OAUTH_STATE_CHECK',
            'ACCOUNT_STATUS_CHECK'
        ))
);

CREATE INDEX IF NOT EXISTS idx_user_login_fail_record_user_id
    ON user_login_fail_record (user_id);

CREATE INDEX IF NOT EXISTS idx_user_login_fail_record_login_identifier
    ON user_login_fail_record (login_identifier);

CREATE INDEX IF NOT EXISTS idx_user_login_fail_record_login_type
    ON user_login_fail_record (login_type);

CREATE INDEX IF NOT EXISTS idx_user_login_fail_record_fail_stage
    ON user_login_fail_record (fail_stage);

CREATE INDEX IF NOT EXISTS idx_user_login_fail_record_fail_type
    ON user_login_fail_record (fail_type);

CREATE INDEX IF NOT EXISTS idx_user_login_fail_record_login_at
    ON user_login_fail_record (login_at);

CREATE INDEX IF NOT EXISTS idx_user_login_fail_record_login_ip
    ON user_login_fail_record (login_ip);

COMMENT ON TABLE user_login_fail_record IS '用户登录失败记录表：仅在登录失败后写入';
COMMENT ON COLUMN user_login_fail_record.id IS '主键，16 byte，应用层使用 HybridSemaphoreIdWorker 生成';
COMMENT ON COLUMN user_login_fail_record.user_id IS '业务用户 ID；若尚未识别到用户可为空';
COMMENT ON COLUMN user_login_fail_record.login_identifier IS '用户本次输入的登录标识，例如邮箱、手机号、用户名';
COMMENT ON COLUMN user_login_fail_record.login_type IS '登录方式：EMAIL / PHONE / GOOGLE / GITHUB / MICROSOFT';
COMMENT ON COLUMN user_login_fail_record.fail_stage IS '失败发生阶段，例如 PASSWORD_CHECK / PHONE_VERIFY_CHECK / OAUTH_STATE_CHECK';
COMMENT ON COLUMN user_login_fail_record.fail_type IS '失败类型，例如 PASSWORD_INCORRECT / PHONE_VOIP_NOT_ALLOWED / OAUTH_STATE_INVALID';
COMMENT ON COLUMN user_login_fail_record.fail_message IS '失败说明，便于排查';
COMMENT ON COLUMN user_login_fail_record.login_ip IS '登录失败时的来源 IP';
COMMENT ON COLUMN user_login_fail_record.user_agent IS '浏览器或客户端标识';
COMMENT ON COLUMN user_login_fail_record.device_fingerprint IS '设备指纹，可为空';
COMMENT ON COLUMN user_login_fail_record.extra_json IS '扩展上下文，例如风控规则、手机号识别结果等';
COMMENT ON COLUMN user_login_fail_record.login_at IS '登录失败时间';
