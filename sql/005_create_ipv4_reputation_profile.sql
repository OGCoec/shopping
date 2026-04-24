-- ============================================
-- 文件名：005_create_ipv4_reputation_profile.sql
-- 说明：IPv4 信誉画像最小版表
-- 约定：
-- 1. 首次出现 IPv4 时调用外部 API 填充
-- 2. reference_score 表示纯 API 参考分
-- 3. base_score 表示系统首次入库分
-- 4. current_score 表示当前分（可根据实际行为调整）
-- 5. 适配：PostgreSQL
-- ============================================

CREATE TABLE IF NOT EXISTS ipv4_reputation_profile (
    -- 直接用 IPv4 字符串作为主键
    ip VARCHAR(64) PRIMARY KEY,

    -- IP 类型：RESIDENTIAL / DATACENTER / MOBILE / BUSINESS / UNKNOWN
    ip_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',

    -- ASN 编号，例如 AS12345
    asn VARCHAR(32),

    -- 运营商 / 组织名称
    provider_name VARCHAR(255),

    -- 纬度
    latitude NUMERIC(10,6),

    -- 经度
    longitude NUMERIC(10,6),

    -- 国家代码，建议使用 ISO 3166-1 alpha-2（如 US/CN/CA）
    country VARCHAR(8),

    -- 是否机房 IP
    is_datacenter BOOLEAN NOT NULL DEFAULT FALSE,

    -- 是否 VPN
    is_vpn BOOLEAN NOT NULL DEFAULT FALSE,

    -- 是否代理
    is_proxy BOOLEAN NOT NULL DEFAULT FALSE,

    -- 是否 Tor
    is_tor BOOLEAN NOT NULL DEFAULT FALSE,

    -- 第三方供应商返回的原始分数（如果有）
    provider_score INT,

    -- 参考分数：纯粹基于第三方 API 返回结果，不考虑你系统内的行为变化
    reference_score INT NOT NULL DEFAULT 0,

    -- 初始分：根据第三方 API 返回结果换算得到，可作为系统首次入库分
    base_score INT NOT NULL DEFAULT 0,

    -- 当前分：在 base_score 基础上叠加你系统内的行为变化
    current_score INT NOT NULL DEFAULT 0,

    -- 第三方数据来源，例如 ipapi.is / abuseipdb / ipinfo
    source_provider VARCHAR(64),

    -- 原始返回，便于后续排查
    raw_json JSONB,

    -- 首次出现时间
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次出现时间
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 最近一次调用外部 API 查询时间
    queried_at TIMESTAMPTZ,

    -- 缓存过期时间，到期后可重新查外部 API
    expires_at TIMESTAMPTZ,

    CONSTRAINT ck_ipv4_reputation_profile_ip_type
        CHECK (ip_type IN ('RESIDENTIAL', 'DATACENTER', 'MOBILE', 'BUSINESS', 'UNKNOWN'))
);

CREATE INDEX IF NOT EXISTS idx_ipv4_reputation_profile_current_score
    ON ipv4_reputation_profile (current_score);

CREATE INDEX IF NOT EXISTS idx_ipv4_reputation_profile_last_seen_at
    ON ipv4_reputation_profile (last_seen_at);

CREATE INDEX IF NOT EXISTS idx_ipv4_reputation_profile_expires_at
    ON ipv4_reputation_profile (expires_at);

CREATE INDEX IF NOT EXISTS idx_ipv4_reputation_profile_asn
    ON ipv4_reputation_profile (asn);

CREATE INDEX IF NOT EXISTS idx_ipv4_reputation_profile_country
    ON ipv4_reputation_profile (country);

COMMENT ON TABLE ipv4_reputation_profile IS 'IPv4 信誉画像最小版表：记录第三方 API 基础情报与系统内动态分数';
COMMENT ON COLUMN ipv4_reputation_profile.ip IS 'IPv4 地址，作为主键';
COMMENT ON COLUMN ipv4_reputation_profile.ip_type IS 'IP 类型：RESIDENTIAL / DATACENTER / MOBILE / BUSINESS / UNKNOWN';
COMMENT ON COLUMN ipv4_reputation_profile.asn IS 'ASN 编号，例如 AS12345';
COMMENT ON COLUMN ipv4_reputation_profile.provider_name IS '运营商 / 组织名称';
COMMENT ON COLUMN ipv4_reputation_profile.latitude IS '纬度';
COMMENT ON COLUMN ipv4_reputation_profile.longitude IS '经度';
COMMENT ON COLUMN ipv4_reputation_profile.country IS '国家代码，建议使用 ISO 3166-1 alpha-2（如 US/CN/CA）';
COMMENT ON COLUMN ipv4_reputation_profile.is_datacenter IS '是否机房 IP';
COMMENT ON COLUMN ipv4_reputation_profile.is_vpn IS '是否 VPN';
COMMENT ON COLUMN ipv4_reputation_profile.is_proxy IS '是否代理';
COMMENT ON COLUMN ipv4_reputation_profile.is_tor IS '是否 Tor';
COMMENT ON COLUMN ipv4_reputation_profile.provider_score IS '第三方供应商原始分数（如果有）';
COMMENT ON COLUMN ipv4_reputation_profile.reference_score IS '参考分数：纯粹基于第三方 API 结果，不考虑行为分';
COMMENT ON COLUMN ipv4_reputation_profile.base_score IS '首次出现时根据第三方情报换算得到的初始分，可作为系统首次入库分';
COMMENT ON COLUMN ipv4_reputation_profile.current_score IS '当前分，后续可根据系统内实际行为调整';
COMMENT ON COLUMN ipv4_reputation_profile.source_provider IS '第三方数据来源';
COMMENT ON COLUMN ipv4_reputation_profile.raw_json IS '第三方 API 原始返回 JSON';
COMMENT ON COLUMN ipv4_reputation_profile.first_seen_at IS '首次出现时间';
COMMENT ON COLUMN ipv4_reputation_profile.last_seen_at IS '最近一次出现时间';
COMMENT ON COLUMN ipv4_reputation_profile.queried_at IS '最近一次调用外部 API 查询时间';
COMMENT ON COLUMN ipv4_reputation_profile.expires_at IS '缓存过期时间';
