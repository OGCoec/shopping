-- ============================================
-- 文件名：035_create_order_card_secret_delivery.sql
-- 说明：创建订单卡密交付记录表
-- 约定：
-- 1. 一条记录代表一张卡密发放给一个已支付订单；
-- 2. order_no、user_id、sku_id、card_secret_id 均为逻辑关联字段，不创建物理外键；
-- 3. 本表只保存卡密库存 ID，不保存卡密明文；
-- 4. 同一张卡密只能交付一次，使用 card_secret_id 唯一约束防止重复发放。
-- ============================================

CREATE TABLE IF NOT EXISTS order_card_secret_delivery (
    -- 订单卡密交付记录主键，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA NOT NULL PRIMARY KEY,

    -- 订单号，对应 trade_order.order_no；本字段为逻辑外键，不创建物理外键
    order_no VARCHAR(64) NOT NULL,

    -- 下单用户 ID，对应 user_profile.id；本字段为逻辑外键，不创建物理外键
    user_id BIGINT NOT NULL,

    -- 商品 SKU ID，对应 product_sku.id；本字段为逻辑外键，不创建物理外键
    sku_id BYTEA NOT NULL,

    -- 卡密库存 ID，对应 card_secret_inventory.id；本字段为逻辑外键，不创建物理外键
    card_secret_id BYTEA NOT NULL,

    -- 交付状态：DELIVERED 已交付，REVOKED 已撤销，REFUNDED 已退款，REPLACED 已替换
    status VARCHAR(32) NOT NULL DEFAULT 'DELIVERED',

    -- 卡密交付时间
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 撤销时间
    revoked_at TIMESTAMPTZ,

    -- 退款关联时间
    refunded_at TIMESTAMPTZ,

    -- 替换时间
    replaced_at TIMESTAMPTZ,

    -- 替换后的新卡密库存 ID，对应 card_secret_inventory.id；本字段为逻辑外键，不创建物理外键
    replaced_by_card_secret_id BYTEA,

    -- 管理员备注或售后备注
    remark TEXT,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 数据版本号，用于后台售后处理、撤销、替换时做乐观锁控制
    version BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT uq_order_card_secret_delivery_card_secret_id
        UNIQUE (card_secret_id),

    CONSTRAINT uq_order_card_secret_delivery_order_card
        UNIQUE (order_no, card_secret_id),

    CONSTRAINT ck_order_card_secret_delivery_id_hybrid_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_order_card_secret_delivery_order_no_not_blank
        CHECK (btrim(order_no) <> ''),

    CONSTRAINT ck_order_card_secret_delivery_user_id
        CHECK (user_id > 0),

    CONSTRAINT ck_order_card_secret_delivery_sku_id_hybrid_bytes
        CHECK (octet_length(sku_id) = 16),

    CONSTRAINT ck_order_card_secret_delivery_card_secret_id_hybrid_bytes
        CHECK (octet_length(card_secret_id) = 16),

    CONSTRAINT ck_order_card_secret_delivery_status
        CHECK (status IN ('DELIVERED', 'REVOKED', 'REFUNDED', 'REPLACED')),

    CONSTRAINT ck_order_card_secret_delivery_replaced_by_card_secret_id_hybrid_bytes
        CHECK (
            replaced_by_card_secret_id IS NULL
            OR octet_length(replaced_by_card_secret_id) = 16
        ),

    CONSTRAINT ck_order_card_secret_delivery_replace_not_self
        CHECK (
            replaced_by_card_secret_id IS NULL
            OR replaced_by_card_secret_id <> card_secret_id
        ),

    CONSTRAINT ck_order_card_secret_delivery_version
        CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_order_card_secret_delivery_order_no
    ON order_card_secret_delivery (order_no, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_order_card_secret_delivery_user_order
    ON order_card_secret_delivery (user_id, order_no, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_order_card_secret_delivery_sku_status
    ON order_card_secret_delivery (sku_id, status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_order_card_secret_delivery_status_created
    ON order_card_secret_delivery (status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_order_card_secret_delivery_delivered_order
    ON order_card_secret_delivery (delivered_at DESC, id DESC);

COMMENT ON TABLE order_card_secret_delivery IS '订单卡密交付记录表：一条记录代表一张卡密发放给一个已支付订单，用于用户订单详情展示、售后追踪、退款关联和防重复交付。本表只保存卡密库存 ID，不保存卡密明文，不创建物理外键。';

COMMENT ON COLUMN order_card_secret_delivery.id IS '订单卡密交付记录主键，由 HybridSemaphoreIdWorker 生成的 16 字节 Hybrid ID；接口和视图使用该字节值转出的 Base62 字符串';
COMMENT ON COLUMN order_card_secret_delivery.order_no IS '订单号，对应 trade_order.order_no；本字段为逻辑外键，不创建物理外键';
COMMENT ON COLUMN order_card_secret_delivery.user_id IS '下单用户 ID，对应 user_profile.id；本字段为逻辑外键，不创建物理外键';
COMMENT ON COLUMN order_card_secret_delivery.sku_id IS '商品 SKU ID，对应 product_sku.id；本字段为 16 字节 Hybrid ID 逻辑外键，不创建物理外键';
COMMENT ON COLUMN order_card_secret_delivery.card_secret_id IS '卡密库存 ID，对应 card_secret_inventory.id；本字段为 16 字节 Hybrid ID 逻辑外键，不创建物理外键；同一张卡密只能交付一次';
COMMENT ON COLUMN order_card_secret_delivery.status IS '交付状态：DELIVERED 已交付，REVOKED 已撤销，REFUNDED 已退款，REPLACED 已替换';
COMMENT ON COLUMN order_card_secret_delivery.delivered_at IS '卡密交付时间';
COMMENT ON COLUMN order_card_secret_delivery.revoked_at IS '卡密交付撤销时间';
COMMENT ON COLUMN order_card_secret_delivery.refunded_at IS '订单退款后卡密交付记录的退款关联时间';
COMMENT ON COLUMN order_card_secret_delivery.replaced_at IS '卡密被售后替换的时间';
COMMENT ON COLUMN order_card_secret_delivery.replaced_by_card_secret_id IS '替换后的新卡密库存 ID，对应 card_secret_inventory.id；本字段为逻辑外键，不创建物理外键';
COMMENT ON COLUMN order_card_secret_delivery.remark IS '管理员备注或售后备注';
COMMENT ON COLUMN order_card_secret_delivery.created_at IS '创建时间';
COMMENT ON COLUMN order_card_secret_delivery.updated_at IS '更新时间';
COMMENT ON COLUMN order_card_secret_delivery.version IS '数据版本号，用于后台售后处理、撤销、替换时做乐观锁控制';
