-- ============================================
-- 文件名：030_view_trade_order_item.sql
-- 说明：trade_order_item 可读视图，展示数据库自增主键、业务订单号和 BYTEA 外键的 Base62 / Hex 形式
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；业务关联使用 order_no，不使用数据库自增 id
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_trade_order_item AS
SELECT
    id,
    order_no,
    user_id,
    spu_id,
    to_base62(sku_id)     AS sku_id_base62, -- 关联 product_sku.id 的 Base62
    encode(sku_id, 'hex') AS sku_id_hex,    -- 兜底 Hex 展示
    sku_code,
    sku_name,
    spec_json,
    sku_image_url,
    quantity,
    sale_price_yuan,
    line_amount_yuan,
    is_hot_sku,
    created_at
FROM trade_order_item;

COMMENT ON VIEW v_trade_order_item IS '订单明细可读视图：id 为数据库自增主键，order_no 为业务订单号，sku_id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_trade_order_item.id IS '数据库订单明细主键，使用 PostgreSQL 自增 BIGINT，只用于数据库内部索引、排序和物理存储优化';
COMMENT ON COLUMN v_trade_order_item.order_no IS '所属订单号，对应 trade_order.order_no，用于订单主表和明细表的业务关联';
COMMENT ON COLUMN v_trade_order_item.user_id IS '下单用户 ID，冗余保存用于用户订单查询和后续分库分表扩展';
COMMENT ON COLUMN v_trade_order_item.spu_id IS '商品 SPU ID，对应 product_spu.id';
COMMENT ON COLUMN v_trade_order_item.sku_id_base62 IS '商品 SKU ID 的 Base62 形式，对应 product_sku.id';
COMMENT ON COLUMN v_trade_order_item.sku_id_hex IS '商品 SKU ID 的 Hex 形式';
COMMENT ON COLUMN v_trade_order_item.sku_code IS '下单时的 SKU 编码快照';
COMMENT ON COLUMN v_trade_order_item.sku_name IS '下单时的 SKU 名称快照';
COMMENT ON COLUMN v_trade_order_item.spec_json IS '下单时的 SKU 规格快照，JSON 对象格式';
COMMENT ON COLUMN v_trade_order_item.sku_image_url IS '下单时的 SKU 图片地址快照';
COMMENT ON COLUMN v_trade_order_item.quantity IS '购买数量';
COMMENT ON COLUMN v_trade_order_item.sale_price_yuan IS '下单时的 SKU 销售单价，单位：元';
COMMENT ON COLUMN v_trade_order_item.line_amount_yuan IS '该明细行金额，单位：元，通常等于 sale_price_yuan * quantity';
COMMENT ON COLUMN v_trade_order_item.is_hot_sku IS '是否为热点 SKU 下单，用于区分 Redis 热点库存链路和普通库存链路';
COMMENT ON COLUMN v_trade_order_item.created_at IS '订单明细创建时间';
