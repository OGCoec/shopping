-- ============================================
-- 文件名：024_view_user_coupon.sql
-- 说明：user_coupon 可读视图，将 16 字节 BYTEA 列转为 Base62 / Hex 展示，并直接展示订单号
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；订单关联使用 order_no
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_user_coupon AS
SELECT
    to_base62(id)                     AS id_base62,                  -- 与后端一致的 Base62 主键
    encode(id, 'hex')                 AS id_hex,                     -- 兜底 Hex 展示
    user_id,
    to_base62(coupon_template_id)     AS coupon_template_id_base62,  -- 关联 coupon_template.id 的 Base62
    encode(coupon_template_id, 'hex') AS coupon_template_id_hex,     -- 兜底 Hex 展示
    status,
    valid_start_at,
    valid_end_at,
    received_at,
    locked_order_no,
    locked_at,
    used_order_no,
    used_at,
    version,
    created_at,
    updated_at
FROM user_coupon;

COMMENT ON VIEW v_user_coupon IS '用户优惠券可读视图：id、coupon_template_id 以 Base62/Hex 展示，订单关联直接展示 order_no';
COMMENT ON COLUMN v_user_coupon.id_base62 IS '用户优惠券 ID 的 Base62 可读形式';
COMMENT ON COLUMN v_user_coupon.id_hex IS '用户优惠券 ID 的 Hex 可读形式';
COMMENT ON COLUMN v_user_coupon.user_id IS '领取优惠券的用户 ID，对应 user_profile.id，不使用物理外键';
COMMENT ON COLUMN v_user_coupon.coupon_template_id_base62 IS '来源优惠券模板 ID 的 Base62 形式，对应 coupon_template.id';
COMMENT ON COLUMN v_user_coupon.coupon_template_id_hex IS '来源优惠券模板 ID 的 Hex 形式';
COMMENT ON COLUMN v_user_coupon.status IS '用户优惠券状态：UNUSED 未使用，LOCKED 下单锁定，USED 已使用，EXPIRED 已过期，REVOKED 已撤销';
COMMENT ON COLUMN v_user_coupon.valid_start_at IS '用户优惠券有效期开始时间';
COMMENT ON COLUMN v_user_coupon.valid_end_at IS '用户优惠券有效期结束时间';
COMMENT ON COLUMN v_user_coupon.received_at IS '领取时间';
COMMENT ON COLUMN v_user_coupon.locked_order_no IS '锁定该优惠券的订单号，对应 trade_order.order_no';
COMMENT ON COLUMN v_user_coupon.locked_at IS '锁定时间';
COMMENT ON COLUMN v_user_coupon.used_order_no IS '使用该优惠券的订单号，对应 trade_order.order_no';
COMMENT ON COLUMN v_user_coupon.used_at IS '使用时间';
COMMENT ON COLUMN v_user_coupon.version IS '数据版本号，用于并发锁券、核销、释放时做乐观锁';
COMMENT ON COLUMN v_user_coupon.created_at IS '创建时间';
COMMENT ON COLUMN v_user_coupon.updated_at IS '更新时间';
