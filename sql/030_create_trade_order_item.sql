-- ============================================
-- 文件名：030_create_trade_order_item.sql
-- 说明：创建订单明细表
-- 约定：
-- 1. 一行代表订单中的一个 SKU 明细；
-- 2. id 使用 PostgreSQL 自增 BIGINT 主键，只作为数据库内部物理主键；
-- 3. order_no 对应 trade_order.order_no，是订单业务关联字段；
-- 4. SKU 名称、编码、规格、图片、现金价格和积分兑换配置保存下单时快照；
-- 5. 即使商品后续修改，订单明细仍保留下单当时的商品信息和积分兑换规则。
-- ============================================

CREATE TABLE IF NOT EXISTS trade_order_item (
    -- 数据库订单明细主键，只用于数据库内部索引和关联排序
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 所属订单号，对应 trade_order.order_no
    order_no VARCHAR(64) NOT NULL,

    -- 下单用户 ID，冗余保存用于用户订单查询和后续分库分表扩展
    user_id BIGINT NOT NULL,

    -- 商品 SPU ID，对应 product_spu.id
    spu_id BIGINT NOT NULL,

    -- 商品 SKU ID，对应 product_sku.id
    sku_id BYTEA NOT NULL,

    -- 下单时的 SKU 编码快照
    sku_code VARCHAR(64) NOT NULL,

    -- 下单时的 SKU 名称快照
    sku_name VARCHAR(128) NOT NULL,

    -- 下单时的 SKU 规格快照，JSON 对象格式
    spec_json JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 下单时的 SKU 图片地址快照
    sku_image_url TEXT,

    -- 购买数量
    quantity INTEGER NOT NULL,

    -- 下单时的 SKU 销售单价，单位：元
    sale_price_yuan NUMERIC(12,2) NOT NULL,

    -- 该明细行现金金额，单位：元
    line_amount_yuan NUMERIC(12,2) NOT NULL,

    -- 下单时该 SKU 是否支持积分兑换
    point_exchange_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    -- 下单时单件 SKU 积分兑换所需积分数量
    point_exchange_points BIGINT NOT NULL DEFAULT 0,

    -- 该明细行积分兑换所需积分数量，通常等于 point_exchange_points * quantity
    line_points BIGINT NOT NULL DEFAULT 0,

    -- 是否为热点 SKU 下单
    is_hot_sku BOOLEAN NOT NULL DEFAULT FALSE,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_trade_order_item_order_no_sku_id
        UNIQUE (order_no, sku_id),

    CONSTRAINT ck_trade_order_item_order_no_not_blank
        CHECK (btrim(order_no) <> ''),

    CONSTRAINT ck_trade_order_item_user_id
        CHECK (user_id > 0),

    CONSTRAINT ck_trade_order_item_spu_id
        CHECK (spu_id > 0),

    CONSTRAINT ck_trade_order_item_sku_id_bytes
        CHECK (octet_length(sku_id) = 16),

    CONSTRAINT ck_trade_order_item_sku_code_not_blank
        CHECK (btrim(sku_code) <> ''),

    CONSTRAINT ck_trade_order_item_sku_name_not_blank
        CHECK (btrim(sku_name) <> ''),

    CONSTRAINT ck_trade_order_item_spec_json_object
        CHECK (jsonb_typeof(spec_json) = 'object'),

    CONSTRAINT ck_trade_order_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT ck_trade_order_item_amount
        CHECK (
            sale_price_yuan >= 0
            AND line_amount_yuan >= 0
        ),

    CONSTRAINT ck_trade_order_item_point_exchange_points
        CHECK (point_exchange_points >= 0),

    CONSTRAINT ck_trade_order_item_line_points
        CHECK (line_points >= 0),

    CONSTRAINT ck_trade_order_item_point_exchange_rule
        CHECK (
            point_exchange_enabled = FALSE
            OR (point_exchange_points > 0 AND line_points > 0)
        )
);

CREATE INDEX IF NOT EXISTS idx_trade_order_item_order_no
    ON trade_order_item (order_no);

CREATE INDEX IF NOT EXISTS idx_trade_order_item_order_created_id
    ON trade_order_item (order_no, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_trade_order_item_user_created
    ON trade_order_item (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_trade_order_item_sku_id
    ON trade_order_item (sku_id);

COMMENT ON TABLE trade_order_item IS '订单明细表：一行代表订单中的一个 SKU 快照。下单后即使商品名称、价格、图片、规格或积分兑换规则发生变化，订单明细仍保留下单当时的商品信息。';

COMMENT ON COLUMN trade_order_item.id IS '数据库订单明细主键，使用 PostgreSQL 自增 BIGINT，只用于数据库内部索引、排序和物理存储优化；业务关联使用 order_no';
COMMENT ON COLUMN trade_order_item.order_no IS '所属订单号，对应 trade_order.order_no，用于订单主表和明细表的业务关联';
COMMENT ON COLUMN trade_order_item.user_id IS '下单用户 ID，冗余保存用于用户订单查询和后续分库分表扩展';
COMMENT ON COLUMN trade_order_item.spu_id IS '商品 SPU ID，对应 product_spu.id';
COMMENT ON COLUMN trade_order_item.sku_id IS '商品 SKU ID，对应 product_sku.id，使用 16 字节 BYTEA';
COMMENT ON COLUMN trade_order_item.sku_code IS '下单时的 SKU 编码快照';
COMMENT ON COLUMN trade_order_item.sku_name IS '下单时的 SKU 名称快照';
COMMENT ON COLUMN trade_order_item.spec_json IS '下单时的 SKU 规格快照，JSON 对象格式';
COMMENT ON COLUMN trade_order_item.sku_image_url IS '下单时的 SKU 图片地址快照';
COMMENT ON COLUMN trade_order_item.quantity IS '购买数量';
COMMENT ON COLUMN trade_order_item.sale_price_yuan IS '下单时的 SKU 销售单价，单位：元';
COMMENT ON COLUMN trade_order_item.line_amount_yuan IS '该明细行现金金额，单位：元，通常等于 sale_price_yuan * quantity';
COMMENT ON COLUMN trade_order_item.point_exchange_enabled IS '下单时该 SKU 是否支持积分兑换';
COMMENT ON COLUMN trade_order_item.point_exchange_points IS '下单时单件 SKU 积分兑换所需积分数量';
COMMENT ON COLUMN trade_order_item.line_points IS '该明细行积分兑换所需积分数量，通常等于 point_exchange_points * quantity';
COMMENT ON COLUMN trade_order_item.is_hot_sku IS '是否为热点 SKU 下单，用于区分 Redis 热点库存链路和普通库存链路';
COMMENT ON COLUMN trade_order_item.created_at IS '订单明细创建时间';
