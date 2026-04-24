-- ============================================
-- 文件名：003_create_user_login_success_record.sql
-- 说明：用户登录成功记录表
-- 约定：仅在登录成功后写入
-- 主键：16 byte（二进制）
-- 不设置外键约束
-- 适配：PostgreSQL
-- ============================================

CREATE TABLE IF NOT EXISTS user_login_success_record (
    -- 主键：应用层生成的 16 byte ID
    id BYTEA PRIMARY KEY,

    -- 业务用户 ID，不加外键约束
    user_id BIGINT NOT NULL,

    -- 登录方式：EMAIL / PHONE / GOOGLE / GITHUB / MICROSOFT
    login_type VARCHAR(32) NOT NULL,

    -- 登录成功时的来源 IP
    login_ip VARCHAR(64),

    -- 浏览器或客户端标识
    user_agent VARCHAR(512),

    -- 设备指纹，可为空
    device_fingerprint VARCHAR(255),

    -- 登录成功时间
    login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_login_success_record_id_16_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_user_login_success_record_login_type
        CHECK (login_type IN ('EMAIL', 'PHONE', 'GOOGLE', 'GITHUB', 'MICROSOFT'))
);

CREATE INDEX IF NOT EXISTS idx_user_login_success_record_user_id
    ON user_login_success_record (user_id);

CREATE INDEX IF NOT EXISTS idx_user_login_success_record_login_type
    ON user_login_success_record (login_type);

CREATE INDEX IF NOT EXISTS idx_user_login_success_record_login_at
    ON user_login_success_record (login_at);

CREATE INDEX IF NOT EXISTS idx_user_login_success_record_login_ip
    ON user_login_success_record (login_ip);

COMMENT ON TABLE user_login_success_record IS '用户登录成功记录表：仅在登录成功后写入';
COMMENT ON COLUMN user_login_success_record.id IS '主键，16 byte，应用层使用 HybridSemaphoreIdWorker 生成';
COMMENT ON COLUMN user_login_success_record.user_id IS '业务用户 ID，不设置外键约束';
COMMENT ON COLUMN user_login_success_record.login_type IS '登录方式：EMAIL / PHONE / GOOGLE / GITHUB / MICROSOFT';
COMMENT ON COLUMN user_login_success_record.login_ip IS '登录成功时的来源 IP';
COMMENT ON COLUMN user_login_success_record.user_agent IS '浏览器或客户端标识';
COMMENT ON COLUMN user_login_success_record.device_fingerprint IS '设备指纹，可为空';
COMMENT ON COLUMN user_login_success_record.login_at IS '登录成功时间';
