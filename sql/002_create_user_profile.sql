-- 用户详细资料表 (PostgreSQL)
-- 该表的 id 与 user_login_identity 表的 user_id 一一对应
CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT PRIMARY KEY, -- 直接使用 user_id 作为主键

    -- 姓名拆分
    first_name VARCHAR(64),
    last_name VARCHAR(64),

    -- 头像信息 (JSONB 格式，可存储多种尺寸或格式)
    avatar JSONB, 

    -- 基础资料
    username VARCHAR(64),
    gender VARCHAR(16) DEFAULT 'UNKNOWN',
    bio VARCHAR(255),
    birthday DATE,

    -- 偏好与地理
    country VARCHAR(64),
    language VARCHAR(16) DEFAULT 'zh-CN',
    timezone VARCHAR(64) DEFAULT 'Asia/Shanghai',

    -- 时间戳
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 约束：性别校验
    CONSTRAINT ck_user_profile_gender 
        CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN'))
);

-- 索引：通过姓名快速搜索
CREATE INDEX IF NOT EXISTS idx_user_profile_full_name ON user_profile (last_name, first_name);

-- 注释
COMMENT ON TABLE user_profile IS '用户详细资料表：存储业务层面的用户信息';
COMMENT ON COLUMN user_profile.id IS '主键，关联 user_login_identity 表的 user_id';
COMMENT ON COLUMN user_profile.avatar IS '头像信息，JSONB 格式，存储如 {"original": "...", "thumbnail": "..."}';
COMMENT ON COLUMN user_profile.username IS '用户名，用于展示与登录后业务识别';
COMMENT ON COLUMN user_profile.gender IS '性别：MALE, FEMALE, OTHER, UNKNOWN';
