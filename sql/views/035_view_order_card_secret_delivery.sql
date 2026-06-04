-- ============================================
-- 文件名：035_view_order_card_secret_delivery.sql
-- 说明：order_card_secret_delivery 可读视图，将 16 字节 BYTEA ID 转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：
-- 1. 视图仅用于只读查看，不参与业务写入；
-- 2. 原表读写仍以 BYTEA 主键和逻辑关联 ID 为准；
-- 3. 本视图不展示卡密明文，卡密明文只能由后端校验权限后解密返回。
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_order_card_secret_delivery AS
SELECT
    to_base62(id)                         AS id_base62,
    encode(id, 'hex')                     AS id_hex,
    order_no,
    user_id,
    to_base62(sku_id)                     AS sku_id_base62,
    encode(sku_id, 'hex')                 AS sku_id_hex,
    to_base62(card_secret_id)             AS card_secret_id_base62,
    encode(card_secret_id, 'hex')         AS card_secret_id_hex,
    status,
    delivered_at,
    revoked_at,
    refunded_at,
    replaced_at,
    CASE
        WHEN replaced_by_card_secret_id IS NULL THEN NULL
        ELSE to_base62(replaced_by_card_secret_id)
    END                                   AS replaced_by_card_secret_id_base62,
    CASE
        WHEN replaced_by_card_secret_id IS NULL THEN NULL
        ELSE encode(replaced_by_card_secret_id, 'hex')
    END                                   AS replaced_by_card_secret_id_hex,
    remark,
    version,
    created_at,
    updated_at
FROM order_card_secret_delivery;

COMMENT ON VIEW v_order_card_secret_delivery IS '订单卡密交付记录可读视图：展示交付记录主键、订单号、用户、SKU、卡密库存 ID、交付状态和售后关联；不展示卡密明文。';

COMMENT ON COLUMN v_order_card_secret_delivery.id_base62 IS '订单卡密交付记录主键 id 的 Base62 可读形式，由 16 字节 Hybrid ID 转出';
COMMENT ON COLUMN v_order_card_secret_delivery.id_hex IS '订单卡密交付记录主键 id 的 Hex 可读形式';
COMMENT ON COLUMN v_order_card_secret_delivery.order_no IS '订单号，对应 trade_order.order_no；逻辑外键，不创建物理外键';
COMMENT ON COLUMN v_order_card_secret_delivery.user_id IS '下单用户 ID，对应 user_profile.id；逻辑外键，不创建物理外键';
COMMENT ON COLUMN v_order_card_secret_delivery.sku_id_base62 IS '商品 SKU ID 的 Base62 可读形式，对应 product_sku.id';
COMMENT ON COLUMN v_order_card_secret_delivery.sku_id_hex IS '商品 SKU ID 的 Hex 可读形式，对应 product_sku.id';
COMMENT ON COLUMN v_order_card_secret_delivery.card_secret_id_base62 IS '卡密库存 ID 的 Base62 可读形式，对应 card_secret_inventory.id';
COMMENT ON COLUMN v_order_card_secret_delivery.card_secret_id_hex IS '卡密库存 ID 的 Hex 可读形式，对应 card_secret_inventory.id';
COMMENT ON COLUMN v_order_card_secret_delivery.status IS '交付状态：DELIVERED 已交付，REVOKED 已撤销，REFUNDED 已退款，REPLACED 已替换';
COMMENT ON COLUMN v_order_card_secret_delivery.delivered_at IS '卡密交付时间';
COMMENT ON COLUMN v_order_card_secret_delivery.revoked_at IS '卡密交付撤销时间';
COMMENT ON COLUMN v_order_card_secret_delivery.refunded_at IS '订单退款后卡密交付记录的退款关联时间';
COMMENT ON COLUMN v_order_card_secret_delivery.replaced_at IS '卡密被售后替换的时间';
COMMENT ON COLUMN v_order_card_secret_delivery.replaced_by_card_secret_id_base62 IS '替换后的新卡密库存 ID 的 Base62 可读形式，对应 card_secret_inventory.id';
COMMENT ON COLUMN v_order_card_secret_delivery.replaced_by_card_secret_id_hex IS '替换后的新卡密库存 ID 的 Hex 可读形式，对应 card_secret_inventory.id';
COMMENT ON COLUMN v_order_card_secret_delivery.remark IS '管理员备注或售后备注';
COMMENT ON COLUMN v_order_card_secret_delivery.version IS '数据版本号，用于后台售后处理、撤销、替换时做乐观锁控制';
COMMENT ON COLUMN v_order_card_secret_delivery.created_at IS '创建时间';
COMMENT ON COLUMN v_order_card_secret_delivery.updated_at IS '更新时间';
