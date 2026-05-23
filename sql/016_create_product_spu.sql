-- ============================================
-- 文件名：016_create_product_spu.sql
-- 说明：商品 SPU 主表
-- 约定：
-- 1. 一行代表一个商品 SPU，例如“红魔 9 Pro”；
-- 2. 商品通过 category_id 挂载到商品分类；
-- 3. 价格、库存、规格不放在本表，后续由 product_sku 表维护；
-- 4. 商品详情图、详情参数、售后说明不放在本表，后续由 product_detail 表维护；
-- 5. 当前不保存 sort_order 和 sales_count，等业务需要排序或销量展示时再扩展。
-- ============================================

CREATE TABLE IF NOT EXISTS product_spu (
    -- 商品 SPU ID，由业务侧雪花 ID 生成
    id BIGINT PRIMARY KEY,

    -- 所属商品分类 ID，对应 product_category.id
    category_id BIGINT NOT NULL,

    -- 商品名称，用于前台展示和后台管理
    name VARCHAR(128) NOT NULL,

    -- 商品副标题，用于列表页或详情页展示卖点
    subtitle VARCHAR(255),

    -- 品牌名称
    brand_name VARCHAR(64),

    -- 商品主图地址，用于商品列表和详情页首图展示
    main_image_url VARCHAR(512),

    -- 商品状态：ACTIVE 上架，DISABLED 下架
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_product_spu_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_product_spu_category_id
    ON product_spu (category_id);

CREATE INDEX IF NOT EXISTS idx_product_spu_status_created_at
    ON product_spu (status, created_at);

CREATE INDEX IF NOT EXISTS idx_product_spu_brand_name
    ON product_spu (brand_name)
    WHERE brand_name IS NOT NULL;

COMMENT ON TABLE product_spu IS '商品 SPU 主表，用于保存商品公共信息';

COMMENT ON COLUMN product_spu.id IS '商品 SPU ID，由业务侧雪花 ID 生成';
COMMENT ON COLUMN product_spu.category_id IS '所属商品分类 ID，对应 product_category.id';
COMMENT ON COLUMN product_spu.name IS '商品名称，用于前台展示和后台管理';
COMMENT ON COLUMN product_spu.subtitle IS '商品副标题，用于列表页或详情页展示卖点';
COMMENT ON COLUMN product_spu.brand_name IS '品牌名称';
COMMENT ON COLUMN product_spu.main_image_url IS '商品主图地址，用于商品列表和详情页首图展示';
COMMENT ON COLUMN product_spu.status IS '商品状态：ACTIVE 上架，DISABLED 下架';
COMMENT ON COLUMN product_spu.created_at IS '创建时间';
COMMENT ON COLUMN product_spu.updated_at IS '更新时间';
