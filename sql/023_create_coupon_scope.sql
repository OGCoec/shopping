-- ============================================
-- 文件名：023_create_coupon_scope.sql
-- 说明：创建优惠券适用范围表
-- 约定：
-- 1. 一行代表某张优惠券模板的一个可用范围；
-- 2. id 和 coupon_template_id 使用 HybridSemaphoreIdWorker 生成的 16 字节 ID；
-- 3. 本表不使用物理外键，分类、SPU、SKU 是否存在由业务层批量校验；
-- 4. scope_target_type 为 CATEGORY、SPU、SKU 时，只允许填写对应的一个目标 ID。
-- ============================================

CREATE TABLE IF NOT EXISTS coupon_scope (
    -- 优惠券适用范围 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA PRIMARY KEY,

    -- 优惠券模板 ID，对应 coupon_template.id
    coupon_template_id BYTEA NOT NULL,

    -- 范围目标类型：CATEGORY 分类，SPU 商品，SKU 具体规格
    scope_target_type VARCHAR(32) NOT NULL,

    -- 商品分类 ID，scope_target_type 为 CATEGORY 时使用
    category_id BIGINT,

    -- 商品 SPU ID，scope_target_type 为 SPU 时使用
    spu_id BIGINT,

    -- 商品 SKU ID，scope_target_type 为 SKU 时使用
    sku_id BYTEA,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_coupon_scope_id_hybridworker
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_coupon_scope_template_id_hybridworker
        CHECK (octet_length(coupon_template_id) = 16),

    CONSTRAINT ck_coupon_scope_sku_id_hybridworker
        CHECK (sku_id IS NULL OR octet_length(sku_id) = 16),

    CONSTRAINT ck_coupon_scope_target_type
        CHECK (scope_target_type IN ('CATEGORY', 'SPU', 'SKU')),

    CONSTRAINT ck_coupon_scope_target_id
        CHECK (
            (
                scope_target_type = 'CATEGORY'
                AND category_id IS NOT NULL
                AND spu_id IS NULL
                AND sku_id IS NULL
            )
            OR
            (
                scope_target_type = 'SPU'
                AND category_id IS NULL
                AND spu_id IS NOT NULL
                AND sku_id IS NULL
            )
            OR
            (
                scope_target_type = 'SKU'
                AND category_id IS NULL
                AND spu_id IS NULL
                AND sku_id IS NOT NULL
            )
        )
);

CREATE INDEX IF NOT EXISTS idx_coupon_scope_template_id
    ON coupon_scope (coupon_template_id);

CREATE INDEX IF NOT EXISTS idx_coupon_scope_template_created
    ON coupon_scope (coupon_template_id, created_at ASC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_scope_category
    ON coupon_scope (coupon_template_id, category_id)
    WHERE scope_target_type = 'CATEGORY';

CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_scope_spu
    ON coupon_scope (coupon_template_id, spu_id)
    WHERE scope_target_type = 'SPU';

CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_scope_sku
    ON coupon_scope (coupon_template_id, sku_id)
    WHERE scope_target_type = 'SKU';

COMMENT ON TABLE coupon_scope IS '优惠券适用范围表：保存某张优惠券可以用于哪些分类、商品 SPU 或商品 SKU';

COMMENT ON COLUMN coupon_scope.id IS '优惠券适用范围 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID';
COMMENT ON COLUMN coupon_scope.coupon_template_id IS '优惠券模板 ID，对应 coupon_template.id，不使用物理外键';
COMMENT ON COLUMN coupon_scope.scope_target_type IS '范围目标类型：CATEGORY 分类，SPU 商品，SKU 具体规格';
COMMENT ON COLUMN coupon_scope.category_id IS '商品分类 ID，scope_target_type 为 CATEGORY 时使用';
COMMENT ON COLUMN coupon_scope.spu_id IS '商品 SPU ID，scope_target_type 为 SPU 时使用';
COMMENT ON COLUMN coupon_scope.sku_id IS '商品 SKU ID，scope_target_type 为 SKU 时使用';
COMMENT ON COLUMN coupon_scope.created_at IS '创建时间';
