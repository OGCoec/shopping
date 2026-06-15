-- ============================================
-- 文件名：029_create_trade_order.sql
-- 说明：创建订单主表
-- 约定：
-- 1. 一行代表用户一次确认下单生成的交易单；
-- 2. id 使用 PostgreSQL 自增 BIGINT 主键，只作为数据库内部物理主键；
-- 3. order_no 保存 HybridSemaphoreIdWorker 生成的 16 字节订单标识的 Base62 编码，用于用户展示、支付平台、客服查询、Redis、MQ 和对账；
-- 4. 异步创建中的状态不写入本表，订单成功落库后的初始状态为 PENDING_PAYMENT；
-- 5. 本表记录订单整体金额、优惠券、支付超时和状态流转信息。
-- ============================================

CREATE TABLE IF NOT EXISTS trade_order (
    -- 数据库订单主键，只用于数据库内部索引和关联排序
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 订单号，用于外部展示、支付、客服查询、Redis、MQ 和对账
    order_no VARCHAR(64) NOT NULL,

    -- 下单用户 ID，对应 user_profile.id
    user_id BIGINT NOT NULL,

    -- 订单状态：PENDING_PAYMENT 待支付，CLOSING 关闭确认中，PAID 已支付，CANCELLED 已取消，CLOSED 已关闭
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',

    -- 订单商品总金额，单位：元，未扣减优惠前的金额
    total_amount_yuan NUMERIC(12,2) NOT NULL,

    -- 订单优惠金额，单位：元
    discount_amount_yuan NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- 订单应付金额，单位：元
    pay_amount_yuan NUMERIC(12,2) NOT NULL,

    -- 本订单使用的用户优惠券 ID，可为空
    user_coupon_id BYTEA,

    -- 下单幂等键，用于防止重复创建订单
    idempotency_key VARCHAR(128) NOT NULL,

    -- 订单支付超时时间
    expire_at TIMESTAMPTZ NOT NULL,

    -- 订单支付成功时间
    paid_at TIMESTAMPTZ,

    -- 订单进入软关闭缓冲期的时间
    closing_at TIMESTAMPTZ,

    -- 订单软关闭缓冲期截止时间，超过后仍未支付成功才最终关闭
    closing_deadline_at TIMESTAMPTZ,

    -- 订单取消时间
    cancelled_at TIMESTAMPTZ,

    -- 订单关闭时间
    closed_at TIMESTAMPTZ,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 数据版本号，用于订单状态变更时做乐观锁控制
    version BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT uq_trade_order_order_no
        UNIQUE (order_no),

    CONSTRAINT uq_trade_order_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT ck_trade_order_order_no_not_blank
        CHECK (btrim(order_no) <> ''),

    CONSTRAINT ck_trade_order_user_id
        CHECK (user_id > 0),

    CONSTRAINT ck_trade_order_status
        CHECK (status IN ('PENDING_PAYMENT', 'CLOSING', 'PAID', 'CANCELLED', 'CLOSED')),

    CONSTRAINT ck_trade_order_amount
        CHECK (
            total_amount_yuan >= 0
            AND discount_amount_yuan >= 0
            AND pay_amount_yuan >= 0
            AND discount_amount_yuan <= total_amount_yuan
        ),

    CONSTRAINT ck_trade_order_user_coupon_id_bytes
        CHECK (user_coupon_id IS NULL OR octet_length(user_coupon_id) = 16),

    CONSTRAINT ck_trade_order_idempotency_key_not_blank
        CHECK (btrim(idempotency_key) <> ''),

    CONSTRAINT ck_trade_order_version
        CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_trade_order_user_created
    ON trade_order (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_trade_order_user_status_created
    ON trade_order (user_id, status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_trade_order_created_id
    ON trade_order (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_trade_order_status_expire
    ON trade_order (status, expire_at, id);

CREATE INDEX IF NOT EXISTS idx_trade_order_status_closing_deadline
    ON trade_order (status, closing_deadline_at, id);

CREATE INDEX IF NOT EXISTS idx_trade_order_user_coupon_id
    ON trade_order (user_coupon_id)
    WHERE user_coupon_id IS NOT NULL;

COMMENT ON TABLE trade_order IS '订单主表：一行代表用户一次确认下单生成的交易单。订单记录用户、金额、优惠、支付状态、超时时间等核心信息，是库存锁定、优惠券锁定、支付、取消、售后等后续流程的业务入口。';

COMMENT ON COLUMN trade_order.id IS '数据库订单主键，使用 PostgreSQL 自增 BIGINT，只用于数据库内部索引、排序和物理存储优化；业务接口、Redis、MQ 和对账使用 order_no';
COMMENT ON COLUMN trade_order.order_no IS '订单号，保存 HybridSemaphoreIdWorker 生成的 16 字节订单标识的 Base62 编码，用于用户展示、支付平台、客服查询、Redis、MQ 和对账';
COMMENT ON COLUMN trade_order.user_id IS '下单用户 ID，对应 user_profile.id';
COMMENT ON COLUMN trade_order.status IS '订单状态：PENDING_PAYMENT 待支付，CLOSING 关闭确认中，PAID 已支付，CANCELLED 已取消，CLOSED 已关闭';
COMMENT ON COLUMN trade_order.total_amount_yuan IS '订单商品总金额，单位：元，未扣减优惠前的金额';
COMMENT ON COLUMN trade_order.discount_amount_yuan IS '订单优惠金额，单位：元，包括优惠券、活动等优惠抵扣';
COMMENT ON COLUMN trade_order.pay_amount_yuan IS '订单应付金额，单位：元，等于商品总金额扣减优惠后的实际待支付金额';
COMMENT ON COLUMN trade_order.user_coupon_id IS '本订单使用的用户优惠券 ID，对应 user_coupon.id，可为空；用于记录订单下单时锁定并在支付成功后核销的那张具体优惠券';
COMMENT ON COLUMN trade_order.idempotency_key IS '下单幂等键，用于防止用户重复点击确认下单导致重复创建订单';
COMMENT ON COLUMN trade_order.expire_at IS '订单支付超时时间，超过该时间仍未支付时可自动关闭并释放库存、优惠券';
COMMENT ON COLUMN trade_order.paid_at IS '订单支付成功时间';
COMMENT ON COLUMN trade_order.closing_at IS '订单进入软关闭缓冲期的时间';
COMMENT ON COLUMN trade_order.closing_deadline_at IS '订单软关闭缓冲期截止时间，超过后仍未支付成功才最终关闭';
COMMENT ON COLUMN trade_order.cancelled_at IS '订单取消时间';
COMMENT ON COLUMN trade_order.closed_at IS '订单关闭时间，通常用于超时未支付或系统原因关闭';
COMMENT ON COLUMN trade_order.created_at IS '订单创建时间';
COMMENT ON COLUMN trade_order.updated_at IS '订单更新时间';
COMMENT ON COLUMN trade_order.version IS '数据版本号，用于订单状态变更时做乐观锁控制';
