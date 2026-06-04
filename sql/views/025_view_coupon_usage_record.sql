-- ============================================
-- 文件名：025_view_coupon_usage_record.sql
-- 说明：coupon_usage_record 可读视图，将 16 字节 BYTEA 列转为 Base62 / Hex 展示，并直接展示订单号
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；订单关联使用 order_no
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_coupon_usage_record AS
SELECT
    to_base62(id)                     AS id_base62,                  -- 与后端一致的 Base62 主键
    encode(id, 'hex')                 AS id_hex,                     -- 兜底 Hex 展示
    to_base62(user_coupon_id)         AS user_coupon_id_base62,      -- 关联 user_coupon.id 的 Base62
    encode(user_coupon_id, 'hex')     AS user_coupon_id_hex,         -- 兜底 Hex 展示
    to_base62(coupon_template_id)     AS coupon_template_id_base62,  -- 关联 coupon_template.id 的 Base62
    encode(coupon_template_id, 'hex') AS coupon_template_id_hex,     -- 兜底 Hex 展示
    user_id,
    order_no,
    action,
    order_amount_yuan,
    discount_amount_yuan,
    idempotency_key,
    created_at
FROM coupon_usage_record;

COMMENT ON VIEW v_coupon_usage_record IS '优惠券核销流水可读视图：id、user_coupon_id、coupon_template_id 以 Base62/Hex 展示，订单关联直接展示 order_no';
COMMENT ON COLUMN v_coupon_usage_record.id_base62 IS '优惠券核销流水 ID 的 Base62 可读形式';
COMMENT ON COLUMN v_coupon_usage_record.id_hex IS '优惠券核销流水 ID 的 Hex 可读形式';
COMMENT ON COLUMN v_coupon_usage_record.user_coupon_id_base62 IS '用户优惠券 ID 的 Base62 形式，对应 user_coupon.id';
COMMENT ON COLUMN v_coupon_usage_record.user_coupon_id_hex IS '用户优惠券 ID 的 Hex 形式';
COMMENT ON COLUMN v_coupon_usage_record.coupon_template_id_base62 IS '优惠券模板 ID 的 Base62 形式，对应 coupon_template.id';
COMMENT ON COLUMN v_coupon_usage_record.coupon_template_id_hex IS '优惠券模板 ID 的 Hex 形式';
COMMENT ON COLUMN v_coupon_usage_record.user_id IS '用户 ID，对应 user_profile.id，不使用物理外键';
COMMENT ON COLUMN v_coupon_usage_record.order_no IS '订单号，对应 trade_order.order_no';
COMMENT ON COLUMN v_coupon_usage_record.action IS '核销动作：LOCK 锁定，CONFIRM 确认使用，RELEASE 释放';
COMMENT ON COLUMN v_coupon_usage_record.order_amount_yuan IS '订单金额，单位：元';
COMMENT ON COLUMN v_coupon_usage_record.discount_amount_yuan IS '优惠金额，单位：元';
COMMENT ON COLUMN v_coupon_usage_record.idempotency_key IS '幂等键，用于防止同一个订单动作重复执行';
COMMENT ON COLUMN v_coupon_usage_record.created_at IS '创建时间';
