-- ============================================
-- 文件名：017_create_product_sku.sql
-- 说明：商品 SKU 表
-- 约定：
-- 1. 一行代表一个可购买的商品规格，例如“红魔 9 Pro 黑色 12G+256G”；
-- 2. SKU 通过 spu_id 归属于 product_spu；
-- 3. 规格信息使用 spec_json 保存，例如 {"颜色":"黑色","内存":"12G","存储":"256G"}；
-- 4. 金额使用元作为单位，使用 NUMERIC(12,2) 保存两位小数；
-- 5. 库存先保存在本表，后续如果库存并发扣减复杂，可以再拆出独立库存表。
-- ============================================

CREATE TABLE IF NOT EXISTS product_sku (
    -- 商品 SKU ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA PRIMARY KEY,

    -- 所属商品 SPU ID，对应 product_spu.id
    spu_id BIGINT NOT NULL,

    -- SKU 编码，用于程序识别、库存对接或幂等导入
    sku_code VARCHAR(64) NOT NULL,

    -- SKU 名称，用于前台展示和后台管理
    sku_name VARCHAR(128) NOT NULL,

    -- SKU 规格信息，JSON 对象格式
    spec_json JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- SKU 图片地址，可用于不同颜色或规格展示不同图片
    sku_image_url TEXT NOT NULL DEFAULT '[]',

    -- 销售单价，单位：元
    price_yuan NUMERIC(12,2) NOT NULL,

    -- 原价，单位：元，可为空
    original_price_yuan NUMERIC(12,2),

    -- 当前库存数量
    stock_quantity INTEGER NOT NULL DEFAULT 0,

    -- SKU 状态：ACTIVE 启用，DISABLED 禁用
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_product_sku_code UNIQUE (sku_code),

    CONSTRAINT uq_product_sku_spu_name UNIQUE (spu_id, sku_name),

    CONSTRAINT ck_product_sku_id_hybrid_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_product_sku_spec_json_object
        CHECK (jsonb_typeof(spec_json) = 'object'),

    CONSTRAINT ck_product_sku_image_urls_array
        CHECK (jsonb_typeof(sku_image_url::jsonb) = 'array'),

    CONSTRAINT ck_product_sku_price_yuan
        CHECK (price_yuan >= 0),

    CONSTRAINT ck_product_sku_original_price_yuan
        CHECK (original_price_yuan IS NULL OR original_price_yuan >= price_yuan),

    CONSTRAINT ck_product_sku_stock_quantity
        CHECK (stock_quantity >= 0),

    CONSTRAINT ck_product_sku_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_product_sku_spu_id
    ON product_sku (spu_id);

CREATE INDEX IF NOT EXISTS idx_product_sku_spu_status
    ON product_sku (spu_id, status);

CREATE INDEX IF NOT EXISTS idx_product_sku_spu_created_id
    ON product_sku (spu_id, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_product_sku_spu_status_created_id
    ON product_sku (spu_id, status, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_product_sku_price_yuan
    ON product_sku (price_yuan);

COMMENT ON TABLE product_sku IS '商品 SKU 表，用于保存具体可购买规格、价格和库存';

COMMENT ON COLUMN product_sku.id IS '商品 SKU ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID；接口和 URL 使用该字节值转出的 Base62 字符串';
COMMENT ON COLUMN product_sku.spu_id IS '所属商品 SPU ID，对应 product_spu.id';
COMMENT ON COLUMN product_sku.sku_code IS 'SKU 编码，用于程序识别、库存对接或幂等导入';
COMMENT ON COLUMN product_sku.sku_name IS 'SKU 名称，用于前台展示和后台管理';
COMMENT ON COLUMN product_sku.spec_json IS 'SKU 规格信息，JSON 对象格式，例如 {"颜色":"黑色","内存":"12G","存储":"256G"}';
COMMENT ON COLUMN product_sku.sku_image_url IS 'SKU 图片地址 JSON 字符串数组，可用于不同颜色或规格展示多张图片';
COMMENT ON COLUMN product_sku.price_yuan IS '销售单价，单位：元';
COMMENT ON COLUMN product_sku.original_price_yuan IS '原价，单位：元，可为空';
COMMENT ON COLUMN product_sku.stock_quantity IS '当前库存数量';
COMMENT ON COLUMN product_sku.status IS 'SKU 状态：ACTIVE 启用，DISABLED 禁用';
COMMENT ON COLUMN product_sku.created_at IS '创建时间';
COMMENT ON COLUMN product_sku.updated_at IS '更新时间';
