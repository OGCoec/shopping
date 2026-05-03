-- 单表登录凭证方案（方案 A）
-- 主键 id 由应用层使用 SnowflakeIdWorker 生成
-- 当前一行代表一个用户的登录资料与第三方绑定信息

CREATE TABLE IF NOT EXISTS user_login_identity (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,

    -- 基础登录字段
    email VARCHAR(255),
    email_password_hash VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,

    phone VARCHAR(32),
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,

    -- 第三方登录绑定
    github_id VARCHAR(255),
    google_id VARCHAR(255),
    microsoft_id VARCHAR(255),

    -- 额外控制字段
    token_version VARCHAR(24) NOT NULL,
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    totp_secret_encrypted VARCHAR(512),
    totp_confirmed_at TIMESTAMPTZ,
    totp_enabled_at TIMESTAMPTZ,
    totp_last_used_step BIGINT,

    -- 状态与时间
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_login_identity_email UNIQUE (email),
    CONSTRAINT uq_user_login_identity_phone UNIQUE (phone),
    CONSTRAINT uq_user_login_identity_github_id UNIQUE (github_id),
    CONSTRAINT uq_user_login_identity_google_id UNIQUE (google_id),
    CONSTRAINT uq_user_login_identity_microsoft_id UNIQUE (microsoft_id),

    CONSTRAINT ck_user_login_identity_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),

    CONSTRAINT ck_user_login_identity_email_password_pair
        CHECK (
            (email IS NULL AND email_password_hash IS NULL)
            OR (email IS NOT NULL)
        ),

    CONSTRAINT ck_user_login_identity_totp_secret_pair
        CHECK (
            (totp_enabled = FALSE)
            OR (totp_secret_encrypted IS NOT NULL AND totp_confirmed_at IS NOT NULL)
        ),

    CONSTRAINT ck_user_login_identity_id_eq_user_id
        CHECK (id = user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_login_identity_user_id
    ON user_login_identity (user_id);

CREATE INDEX IF NOT EXISTS idx_user_login_identity_status
    ON user_login_identity (status);

CREATE INDEX IF NOT EXISTS idx_user_login_identity_last_login_at
    ON user_login_identity (last_login_at);

CREATE INDEX IF NOT EXISTS idx_user_login_identity_token_version
    ON user_login_identity (token_version);

COMMENT ON TABLE user_login_identity IS '单表登录凭证方案：邮箱、手机号、Google、GitHub、Microsoft 绑定统一存储';

COMMENT ON COLUMN user_login_identity.id IS '主键，应用层使用 SnowflakeIdWorker 生成';
COMMENT ON COLUMN user_login_identity.user_id IS '所属用户 ID，对应用户主体';
COMMENT ON COLUMN user_login_identity.email IS '邮箱，建议应用层统一转小写后入库';
COMMENT ON COLUMN user_login_identity.email_password_hash IS '邮箱密码哈希，仅邮箱密码登录使用';
COMMENT ON COLUMN user_login_identity.email_verified IS '邮箱是否已验证';
COMMENT ON COLUMN user_login_identity.phone IS '手机号，建议应用层统一转为 E.164 格式后入库';
COMMENT ON COLUMN user_login_identity.phone_verified IS '手机号是否已验证';
COMMENT ON COLUMN user_login_identity.github_id IS 'GitHub 平台用户唯一标识';
COMMENT ON COLUMN user_login_identity.google_id IS 'Google 平台用户唯一标识';
COMMENT ON COLUMN user_login_identity.microsoft_id IS 'Microsoft 平台用户唯一标识';
COMMENT ON COLUMN user_login_identity.token_version IS '令牌版本号字符串，用于令牌整体失效控制';
COMMENT ON COLUMN user_login_identity.totp_enabled IS '是否启用 TOTP 身份验证器';
COMMENT ON COLUMN user_login_identity.totp_secret_encrypted IS 'TOTP Base32 secret 加密后的存储值';
COMMENT ON COLUMN user_login_identity.totp_confirmed_at IS 'TOTP 绑定验证码确认成功时间';
COMMENT ON COLUMN user_login_identity.totp_enabled_at IS 'TOTP 正式启用时间';
COMMENT ON COLUMN user_login_identity.totp_last_used_step IS 'TOTP 上一次验证成功的时间步，用于防止同一验证码重复使用';
COMMENT ON COLUMN user_login_identity.status IS '账号状态：ACTIVE / DISABLED / LOCKED';
COMMENT ON COLUMN user_login_identity.last_login_at IS '最近一次登录时间';
COMMENT ON COLUMN user_login_identity.created_at IS '创建时间';
COMMENT ON COLUMN user_login_identity.updated_at IS '更新时间';
