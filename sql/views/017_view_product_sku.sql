-- ============================================
-- 文件名：017_view_product_sku.sql
-- 说明：product_sku 可读视图，将 16 字节 BYTEA 主键转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；原表读写仍走 BYTEA 主键
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_product_sku AS
SELECT
    to_base62(id)     AS id_base62,   -- 与后端、URL 一致的 Base62 主键
    encode(id, 'hex') AS id_hex,      -- 兜底 Hex 展示
    spu_id,
    sku_code,
    sku_name,
    spec_json,
    sku_image_url,
    price_yuan,
    original_price_yuan,
    stock_quantity,
    status,
    created_at,
    updated_at
FROM product_sku;

COMMENT ON VIEW v_product_sku IS '商品 SKU 可读视图：主键 id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_product_sku.id_base62 IS '主键 id 的 Base62 可读形式，与后端、URL 一致';
COMMENT ON COLUMN v_product_sku.id_hex IS '主键 id 的 Hex 可读形式';
COMMENT ON COLUMN v_product_sku.spu_id IS '所属商品 SPU ID，对应 product_spu.id';
COMMENT ON COLUMN v_product_sku.sku_code IS 'SKU 编码，用于程序识别、库存对接或幂等导入';
COMMENT ON COLUMN v_product_sku.sku_name IS 'SKU 名称，用于前台展示和后台管理';
COMMENT ON COLUMN v_product_sku.spec_json IS 'SKU 规格信息，JSON 对象格式，例如 {"颜色":"黑色","内存":"12G","存储":"256G"}';
COMMENT ON COLUMN v_product_sku.sku_image_url IS 'SKU 图片地址 JSON 字符串数组，可用于不同颜色或规格展示多张图片';
COMMENT ON COLUMN v_product_sku.price_yuan IS '销售单价，单位：元';
COMMENT ON COLUMN v_product_sku.original_price_yuan IS '原价，单位：元，可为空';
COMMENT ON COLUMN v_product_sku.stock_quantity IS '当前库存数量';
COMMENT ON COLUMN v_product_sku.status IS 'SKU 状态：ACTIVE 启用，DISABLED 禁用';
COMMENT ON COLUMN v_product_sku.created_at IS '创建时间';
COMMENT ON COLUMN v_product_sku.updated_at IS '更新时间';