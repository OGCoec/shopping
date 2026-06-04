-- ============================================
-- 文件名：023_view_coupon_scope.sql
-- 说明：coupon_scope 可读视图，将 16 字节 BYTEA 列转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；sku_id 可为空，空值转码结果为 NULL
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_coupon_scope AS
SELECT
    to_base62(id)                     AS id_base62,                  -- 与后端一致的 Base62 主键
    encode(id, 'hex')                 AS id_hex,                     -- 兜底 Hex 展示
    to_base62(coupon_template_id)     AS coupon_template_id_base62,  -- 关联 coupon_template.id 的 Base62
    encode(coupon_template_id, 'hex') AS coupon_template_id_hex,     -- 兜底 Hex 展示
    scope_target_type,
    category_id,
    spu_id,
    to_base62(sku_id)                 AS sku_id_base62,              -- 关联 product_sku.id 的 Base62，可为空
    encode(sku_id, 'hex')             AS sku_id_hex,                 -- 兜底 Hex 展示，可为空
    created_at
FROM coupon_scope;

COMMENT ON VIEW v_coupon_scope IS '优惠券适用范围可读视图：id、coupon_template_id、sku_id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_coupon_scope.id_base62 IS '优惠券适用范围 ID 的 Base62 可读形式';
COMMENT ON COLUMN v_coupon_scope.id_hex IS '优惠券适用范围 ID 的 Hex 可读形式';
COMMENT ON COLUMN v_coupon_scope.coupon_template_id_base62 IS '优惠券模板 ID 的 Base62 形式，对应 coupon_template.id';
COMMENT ON COLUMN v_coupon_scope.coupon_template_id_hex IS '优惠券模板 ID 的 Hex 形式';
COMMENT ON COLUMN v_coupon_scope.scope_target_type IS '范围目标类型：CATEGORY 分类，SPU 商品，SKU 具体规格';
COMMENT ON COLUMN v_coupon_scope.category_id IS '商品分类 ID，scope_target_type 为 CATEGORY 时使用';
COMMENT ON COLUMN v_coupon_scope.spu_id IS '商品 SPU ID，scope_target_type 为 SPU 时使用';
COMMENT ON COLUMN v_coupon_scope.sku_id_base62 IS '商品 SKU ID 的 Base62 形式，scope_target_type 为 SKU 时使用，可为空';
COMMENT ON COLUMN v_coupon_scope.sku_id_hex IS '商品 SKU ID 的 Hex 形式，可为空';
COMMENT ON COLUMN v_coupon_scope.created_at IS '创建时间';