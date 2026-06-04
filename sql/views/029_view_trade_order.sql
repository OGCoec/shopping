-- ============================================
-- 文件名：029_view_trade_order.sql
-- 说明：trade_order 可读视图，展示数据库自增主键、业务订单号和 BYTEA 外键的 Base62 / Hex 形式
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；业务查询使用 order_no，不使用数据库自增 id
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_trade_order AS
SELECT
    id,
    order_no,
    user_id,
    status,
    total_amount_yuan,
    discount_amount_yuan,
    pay_amount_yuan,
    to_base62(user_coupon_id)     AS user_coupon_id_base62, -- 关联 user_coupon.id 的 Base62
    encode(user_coupon_id, 'hex') AS user_coupon_id_hex,    -- 兜底 Hex 展示
    idempotency_key,
    expire_at,
    paid_at,
    closing_at,
    closing_deadline_at,
    cancelled_at,
    closed_at,
    version,
    created_at,
    updated_at
FROM trade_order;

COMMENT ON VIEW v_trade_order IS '订单主表可读视图：id 为数据库自增主键，order_no 为业务订单号，user_coupon_id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_trade_order.id IS '数据库订单主键，使用 PostgreSQL 自增 BIGINT，只用于数据库内部索引、排序和物理存储优化';
COMMENT ON COLUMN v_trade_order.order_no IS '订单号，保存 HybridSemaphoreIdWorker 生成的 16 字节订单标识的 Base62 编码，用于用户展示、支付平台、客服查询、Redis、MQ 和对账';
COMMENT ON COLUMN v_trade_order.user_id IS '下单用户 ID，对应 user_profile.id';
COMMENT ON COLUMN v_trade_order.status IS '订单状态：PENDING_PAYMENT 待支付，CLOSING 关闭确认中，PAID 已支付，CANCELLED 已取消，CLOSED 已关闭';
COMMENT ON COLUMN v_trade_order.total_amount_yuan IS '订单商品总金额，单位：元，未扣减优惠前的金额';
COMMENT ON COLUMN v_trade_order.discount_amount_yuan IS '订单优惠金额，单位：元，包括优惠券、活动等优惠抵扣';
COMMENT ON COLUMN v_trade_order.pay_amount_yuan IS '订单应付金额，单位：元，等于商品总金额扣减优惠后的实际待支付金额';
COMMENT ON COLUMN v_trade_order.user_coupon_id_base62 IS '本订单使用的用户优惠券 ID 的 Base62 形式，对应 user_coupon.id，可为空';
COMMENT ON COLUMN v_trade_order.user_coupon_id_hex IS '本订单使用的用户优惠券 ID 的 Hex 形式，可为空';
COMMENT ON COLUMN v_trade_order.idempotency_key IS '下单幂等键，用于防止用户重复点击确认下单导致重复创建订单';
COMMENT ON COLUMN v_trade_order.expire_at IS '订单支付超时时间，超过该时间仍未支付时可自动关闭并释放库存、优惠券';
COMMENT ON COLUMN v_trade_order.paid_at IS '订单支付成功时间';
COMMENT ON COLUMN v_trade_order.closing_at IS '订单进入软关闭缓冲期的时间';
COMMENT ON COLUMN v_trade_order.closing_deadline_at IS '订单软关闭缓冲期截止时间，超过后仍未支付成功才最终关闭';
COMMENT ON COLUMN v_trade_order.cancelled_at IS '订单取消时间';
COMMENT ON COLUMN v_trade_order.closed_at IS '订单关闭时间，通常用于超时未支付或系统原因关闭';
COMMENT ON COLUMN v_trade_order.version IS '数据版本号，用于订单状态变更时做乐观锁控制';
COMMENT ON COLUMN v_trade_order.created_at IS '订单创建时间';
COMMENT ON COLUMN v_trade_order.updated_at IS '订单更新时间';
