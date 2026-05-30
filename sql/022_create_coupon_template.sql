-- ============================================
-- 文件名：022_create_coupon_template.sql
-- 说明：创建优惠券模板表
-- 约定：
-- 1. 一行代表一种优惠券规则，例如“满 100 减 20”或“8 折券”；
-- 2. id 由 HybridSemaphoreIdWorker 生成，数据库保存为 16 字节 BYTEA；
-- 3. 本表不使用物理外键，关联数据由业务层批量校验；
-- 4. 金额字段单位统一为元。
-- ============================================

CREATE TABLE IF NOT EXISTS coupon_template (
    -- 优惠券模板 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA PRIMARY KEY,

    -- 优惠券编码，用于后台识别、幂等导入或运营配置
    coupon_code VARCHAR(64) NOT NULL,

    -- 优惠券名称，用于前台展示和后台管理
    name VARCHAR(128) NOT NULL,

    -- 优惠类型：AMOUNT 固定金额减免，PERCENT 按比例折扣
    discount_type VARCHAR(32) NOT NULL,

    -- 最低使用金额，单位：元，0 表示无门槛
    threshold_amount_yuan NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- 固定减免金额，单位：元，discount_type 为 AMOUNT 时使用
    discount_amount_yuan NUMERIC(12,2),

    -- 折扣比例，discount_type 为 PERCENT 时使用，例如 0.8000 表示 8 折
    discount_rate NUMERIC(5,4),

    -- 折扣券最高减免金额，单位：元，可为空表示不封顶
    max_discount_amount_yuan NUMERIC(12,2),

    -- 优惠券发放总数量
    total_quantity INTEGER NOT NULL,

    -- 优惠券剩余可领取数量
    remaining_quantity INTEGER NOT NULL,

    -- 单个用户最多可领取数量
    per_user_limit INTEGER NOT NULL DEFAULT 1,

    -- 适用范围类型：ALL 全场，CATEGORY 分类，SPU 商品，SKU 具体规格
    scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL',

    -- 可领取开始时间
    receive_start_at TIMESTAMPTZ NOT NULL,

    -- 可领取结束时间
    receive_end_at TIMESTAMPTZ NOT NULL,

    -- 优惠券有效期开始时间
    valid_start_at TIMESTAMPTZ NOT NULL,

    -- 优惠券有效期结束时间
    valid_end_at TIMESTAMPTZ NOT NULL,

    -- 模板状态：DRAFT 草稿，ACTIVE 启用，DISABLED 禁用，EXPIRED 已过期
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',

    -- 数据版本号，用于后续并发更新库存或状态时做乐观锁
    version BIGINT NOT NULL DEFAULT 1,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_coupon_template_code
        UNIQUE (coupon_code),

    CONSTRAINT ck_coupon_template_id_hybridworker
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_coupon_template_discount_type
        CHECK (discount_type IN ('AMOUNT', 'PERCENT')),

    CONSTRAINT ck_coupon_template_discount_value
        CHECK (
            (
                discount_type = 'AMOUNT'
                AND discount_amount_yuan IS NOT NULL
                AND discount_amount_yuan >= 0
                AND discount_rate IS NULL
                AND max_discount_amount_yuan IS NULL
            )
            OR
            (
                discount_type = 'PERCENT'
                AND discount_amount_yuan IS NULL
                AND discount_rate IS NOT NULL
                AND discount_rate > 0
                AND discount_rate <= 1
                AND (max_discount_amount_yuan IS NULL OR max_discount_amount_yuan >= 0)
            )
        ),

    CONSTRAINT ck_coupon_template_threshold_amount
        CHECK (threshold_amount_yuan >= 0),

    CONSTRAINT ck_coupon_template_quantity
        CHECK (total_quantity >= 0 AND remaining_quantity >= 0 AND remaining_quantity <= total_quantity),

    CONSTRAINT ck_coupon_template_per_user_limit
        CHECK (per_user_limit > 0),

    CONSTRAINT ck_coupon_template_scope_type
        CHECK (scope_type IN ('ALL', 'CATEGORY', 'SPU', 'SKU')),

    CONSTRAINT ck_coupon_template_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED', 'EXPIRED', 'DELETED')),

    CONSTRAINT ck_coupon_template_time_range
        CHECK (receive_end_at > receive_start_at AND valid_end_at > valid_start_at),

    CONSTRAINT ck_coupon_template_version
        CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_coupon_template_status_receive_time
    ON coupon_template (status, receive_start_at, receive_end_at);

CREATE INDEX IF NOT EXISTS idx_coupon_template_valid_time
    ON coupon_template (valid_start_at, valid_end_at);

COMMENT ON TABLE coupon_template IS '优惠券模板表：保存优惠券规则，例如满减券、折扣券、发放数量、有效期和状态';

COMMENT ON COLUMN coupon_template.id IS '优惠券模板 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID；接口可使用该字节值转出的 Base62 字符串';
COMMENT ON COLUMN coupon_template.coupon_code IS '优惠券编码，用于后台识别、幂等导入或运营配置';
COMMENT ON COLUMN coupon_template.name IS '优惠券名称，用于前台展示和后台管理';
COMMENT ON COLUMN coupon_template.discount_type IS '优惠类型：AMOUNT 固定金额减免，PERCENT 按比例折扣';
COMMENT ON COLUMN coupon_template.threshold_amount_yuan IS '最低使用金额，单位：元，0 表示无门槛';
COMMENT ON COLUMN coupon_template.discount_amount_yuan IS '固定减免金额，单位：元，discount_type 为 AMOUNT 时使用';
COMMENT ON COLUMN coupon_template.discount_rate IS '折扣比例，discount_type 为 PERCENT 时使用，例如 0.8000 表示 8 折';
COMMENT ON COLUMN coupon_template.max_discount_amount_yuan IS '折扣券最高减免金额，单位：元，可为空表示不封顶';
COMMENT ON COLUMN coupon_template.total_quantity IS '优惠券发放总数量';
COMMENT ON COLUMN coupon_template.remaining_quantity IS '优惠券剩余可领取数量';
COMMENT ON COLUMN coupon_template.per_user_limit IS '单个用户最多可领取数量';
COMMENT ON COLUMN coupon_template.scope_type IS '适用范围类型：ALL 全场，CATEGORY 分类，SPU 商品，SKU 具体规格';
COMMENT ON COLUMN coupon_template.receive_start_at IS '可领取开始时间';
COMMENT ON COLUMN coupon_template.receive_end_at IS '可领取结束时间';
COMMENT ON COLUMN coupon_template.valid_start_at IS '优惠券有效期开始时间';
COMMENT ON COLUMN coupon_template.valid_end_at IS '优惠券有效期结束时间';
COMMENT ON COLUMN coupon_template.status IS '模板状态：DRAFT 草稿，ACTIVE 启用，DISABLED 禁用，EXPIRED 已过期，DELETED 已删除';
COMMENT ON COLUMN coupon_template.version IS '数据版本号，用于后续并发更新库存或状态时做乐观锁';
COMMENT ON COLUMN coupon_template.created_at IS '创建时间';
COMMENT ON COLUMN coupon_template.updated_at IS '更新时间';
