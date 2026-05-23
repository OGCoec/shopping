-- ============================================
-- 文件名：018_create_product_detail.sql
-- 说明：商品详情表
-- 约定：
-- 1. 一行代表一个商品 SPU 的详情信息；
-- 2. product_detail 与 product_spu 是一对一关系，通过 spu_id 关联；
-- 3. image_urls 保存商品展示图片集合，例如主图、轮播图；
-- 4. detail_image_urls 保存商品详情图片集合，例如详情长图、参数图；
-- 5. attributes 保存商品参数 JSON 对象，例如品牌、型号、尺寸、材质等。
-- ============================================

CREATE TABLE IF NOT EXISTS product_detail (
    -- 商品详情 ID，由业务侧雪花 ID 生成
    id BIGINT PRIMARY KEY,

    -- 所属商品 SPU ID，对应 product_spu.id
    spu_id BIGINT NOT NULL,

    -- 商品展示图片集合，JSON 数组格式
    image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 商品详情图片集合，JSON 数组格式
    detail_image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 商品参数，JSON 对象格式
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 商品文字详情
    description TEXT,

    -- 售后说明
    after_sale TEXT,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_product_detail_spu_id UNIQUE (spu_id),

    CONSTRAINT ck_product_detail_image_urls_array
        CHECK (jsonb_typeof(image_urls) = 'array'),

    CONSTRAINT ck_product_detail_detail_image_urls_array
        CHECK (jsonb_typeof(detail_image_urls) = 'array'),

    CONSTRAINT ck_product_detail_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object')
);

COMMENT ON TABLE product_detail IS '商品详情表，用于保存商品详情页展示内容';

COMMENT ON COLUMN product_detail.id IS '商品详情 ID，由业务侧雪花 ID 生成';
COMMENT ON COLUMN product_detail.spu_id IS '所属商品 SPU ID，对应 product_spu.id';
COMMENT ON COLUMN product_detail.image_urls IS '商品展示图片 URL 字符串数组，数组顺序即展示顺序，例如 ["https://example.com/main.png","https://example.com/slide-1.png"]';
COMMENT ON COLUMN product_detail.detail_image_urls IS '商品详情图片 URL 字符串数组，数组顺序即展示顺序，例如 ["https://example.com/detail-1.png"]';
COMMENT ON COLUMN product_detail.attributes IS '商品参数，JSON 对象格式，例如 {"品牌":"红魔","型号":"红魔 9 Pro","屏幕尺寸":"6.8 英寸"}';
COMMENT ON COLUMN product_detail.description IS '商品文字详情';
COMMENT ON COLUMN product_detail.after_sale IS '售后说明';
COMMENT ON COLUMN product_detail.created_at IS '创建时间';
COMMENT ON COLUMN product_detail.updated_at IS '更新时间';
