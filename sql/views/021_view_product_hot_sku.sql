-- ============================================
-- 文件名：021_view_product_hot_sku.sql
-- 说明：product_hot_sku 可读视图，将 16 字节 BYTEA 列转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；原表读写仍走 BYTEA 列
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_product_hot_sku AS
SELECT
    to_base62(id)         AS id_base62,      -- 与后端一致的 Base62 主键
    encode(id, 'hex')     AS id_hex,         -- 兜底 Hex 展示
    spu_id,
    to_base62(sku_id)     AS sku_id_base62,  -- 关联 product_sku.id 的 Base62
    encode(sku_id, 'hex') AS sku_id_hex,     -- 兜底 Hex 展示
    stock_quantity,
    remaining_quantity,
    status,
    start_at,
    end_at,
    version,
    created_at,
    updated_at
FROM product_hot_sku;

COMMENT ON VIEW v_product_hot_sku IS '热点 SKU 配置可读视图：id、sku_id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_product_hot_sku.id_base62 IS '热点配置 ID 的 Base62 可读形式';
COMMENT ON COLUMN v_product_hot_sku.id_hex IS '热点配置 ID 的 Hex 可读形式';
COMMENT ON COLUMN v_product_hot_sku.spu_id IS '所属商品 SPU ID';
COMMENT ON COLUMN v_product_hot_sku.sku_id_base62 IS '热点 SKU ID 的 Base62 形式，对应 product_sku.id';
COMMENT ON COLUMN v_product_hot_sku.sku_id_hex IS '热点 SKU ID 的 Hex 形式';
COMMENT ON COLUMN v_product_hot_sku.stock_quantity IS '热点活动配置的总库存';
COMMENT ON COLUMN v_product_hot_sku.remaining_quantity IS '热点活动当前剩余库存';
COMMENT ON COLUMN v_product_hot_sku.status IS '热点状态：ENABLED 启用，DISABLED 禁用，SOLD_OUT 售罄';
COMMENT ON COLUMN v_product_hot_sku.start_at IS '热点活动开始时间，Redis 不按该字段设置 TTL';
COMMENT ON COLUMN v_product_hot_sku.end_at IS '热点活动结束时间，Redis 不按该字段设置 TTL';
COMMENT ON COLUMN v_product_hot_sku.version IS '热点配置版本，每次覆盖配置递增';
COMMENT ON COLUMN v_product_hot_sku.created_at IS '创建时间';
COMMENT ON COLUMN v_product_hot_sku.updated_at IS '更新时间';