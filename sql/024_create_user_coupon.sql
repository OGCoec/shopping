-- ============================================
-- 文件名：024_create_user_coupon.sql
-- 说明：创建用户优惠券表
-- 约定：
-- 1. 一行代表用户领取到的一张具体优惠券；
-- 2. id 和 coupon_template_id 使用 HybridSemaphoreIdWorker 生成的 16 字节 ID；
-- 3. 本表不使用物理外键，用户和优惠券模板是否存在由业务层批量校验；
-- 4. version 用于后续并发锁券、核销、释放时做乐观锁。
-- ============================================

CREATE TABLE IF NOT EXISTS user_coupon (
    -- 用户优惠券 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA PRIMARY KEY,

    -- 领取优惠券的用户 ID，对应 user_profile.id
    user_id BIGINT NOT NULL,

    -- 来源优惠券模板 ID，对应 coupon_template.id
    coupon_template_id BYTEA NOT NULL,

    -- 用户优惠券状态：UNUSED 未使用，LOCKED 下单锁定，USED 已使用，EXPIRED 已过期，REVOKED 已撤销
    status VARCHAR(32) NOT NULL DEFAULT 'UNUSED',

    -- 用户优惠券有效期开始时间
    valid_start_at TIMESTAMPTZ NOT NULL,

    -- 用户优惠券有效期结束时间
    valid_end_at TIMESTAMPTZ NOT NULL,

    -- 领取时间
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 锁定该优惠券的订单 ID，下单锁券时写入
    locked_order_id BIGINT,

    -- 锁定时间
    locked_at TIMESTAMPTZ,

    -- 使用该优惠券的订单 ID，支付成功确认核销时写入
    used_order_id BIGINT,

    -- 使用时间
    used_at TIMESTAMPTZ,

    -- 数据版本号，用于并发锁券、核销、释放时做乐观锁
    version BIGINT NOT NULL DEFAULT 1,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_coupon_id_hybridworker
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_user_coupon_template_id_hybridworker
        CHECK (octet_length(coupon_template_id) = 16),

    CONSTRAINT ck_user_coupon_status
        CHECK (status IN ('UNUSED', 'LOCKED', 'USED', 'EXPIRED', 'REVOKED')),

    CONSTRAINT ck_user_coupon_time_range
        CHECK (valid_end_at > valid_start_at),

    CONSTRAINT ck_user_coupon_version
        CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_user_coupon_user_status_valid
    ON user_coupon (user_id, status, valid_end_at);

CREATE INDEX IF NOT EXISTS idx_user_coupon_template_user
    ON user_coupon (coupon_template_id, user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_coupon_user_template
    ON user_coupon (user_id, coupon_template_id);

COMMENT ON TABLE user_coupon IS '用户优惠券表：保存用户实际领取到的优惠券，以及未使用、锁定、已使用、过期等状态';

COMMENT ON COLUMN user_coupon.id IS '用户优惠券 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID';
COMMENT ON COLUMN user_coupon.user_id IS '领取优惠券的用户 ID，对应 user_profile.id，不使用物理外键';
COMMENT ON COLUMN user_coupon.coupon_template_id IS '来源优惠券模板 ID，对应 coupon_template.id，不使用物理外键';
COMMENT ON COLUMN user_coupon.status IS '用户优惠券状态：UNUSED 未使用，LOCKED 下单锁定，USED 已使用，EXPIRED 已过期，REVOKED 已撤销';
COMMENT ON COLUMN user_coupon.valid_start_at IS '用户优惠券有效期开始时间';
COMMENT ON COLUMN user_coupon.valid_end_at IS '用户优惠券有效期结束时间';
COMMENT ON COLUMN user_coupon.received_at IS '领取时间';
COMMENT ON COLUMN user_coupon.locked_order_id IS '锁定该优惠券的订单 ID，下单锁券时写入';
COMMENT ON COLUMN user_coupon.locked_at IS '锁定时间';
COMMENT ON COLUMN user_coupon.used_order_id IS '使用该优惠券的订单 ID，支付成功确认核销时写入';
COMMENT ON COLUMN user_coupon.used_at IS '使用时间';
COMMENT ON COLUMN user_coupon.version IS '数据版本号，用于并发锁券、核销、释放时做乐观锁';
COMMENT ON COLUMN user_coupon.created_at IS '创建时间';
COMMENT ON COLUMN user_coupon.updated_at IS '更新时间';
