-- ============================================
-- 文件名：025_create_coupon_usage_record.sql
-- 说明：创建优惠券核销流水表
-- 约定：
-- 1. 一行代表一次订单优惠券动作，例如锁定、确认使用、释放；
-- 2. id、user_coupon_id、coupon_template_id 使用 HybridSemaphoreIdWorker 生成的 16 字节 ID；
-- 3. 本表不使用物理外键，用户优惠券和优惠券模板是否存在由业务层批量校验；
-- 4. idempotency_key 用于防止同一个订单动作重复执行。
-- ============================================

CREATE TABLE IF NOT EXISTS coupon_usage_record (
    -- 优惠券核销流水 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA PRIMARY KEY,

    -- 用户优惠券 ID，对应 user_coupon.id
    user_coupon_id BYTEA NOT NULL,

    -- 优惠券模板 ID，对应 coupon_template.id
    coupon_template_id BYTEA NOT NULL,

    -- 用户 ID，对应 user_profile.id
    user_id BIGINT NOT NULL,

    -- 订单 ID，当前按 BIGINT 保存
    order_id BIGINT NOT NULL,

    -- 核销动作：LOCK 锁定，CONFIRM 确认使用，RELEASE 释放
    action VARCHAR(32) NOT NULL,

    -- 订单金额，单位：元
    order_amount_yuan NUMERIC(12,2) NOT NULL,

    -- 优惠金额，单位：元
    discount_amount_yuan NUMERIC(12,2) NOT NULL,

    -- 幂等键，用于防止同一个订单动作重复执行
    idempotency_key VARCHAR(128) NOT NULL,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_coupon_usage_record_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT ck_coupon_usage_record_id_hybridworker
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_coupon_usage_record_user_coupon_id_hybridworker
        CHECK (octet_length(user_coupon_id) = 16),

    CONSTRAINT ck_coupon_usage_record_template_id_hybridworker
        CHECK (octet_length(coupon_template_id) = 16),

    CONSTRAINT ck_coupon_usage_record_action
        CHECK (action IN ('LOCK', 'CONFIRM', 'RELEASE')),

    CONSTRAINT ck_coupon_usage_record_amount
        CHECK (
            order_amount_yuan >= 0
            AND discount_amount_yuan >= 0
            AND discount_amount_yuan <= order_amount_yuan
        )
);

CREATE INDEX IF NOT EXISTS idx_coupon_usage_record_order_id
    ON coupon_usage_record (order_id);

CREATE INDEX IF NOT EXISTS idx_coupon_usage_record_user_coupon_id
    ON coupon_usage_record (user_coupon_id);

COMMENT ON TABLE coupon_usage_record IS '优惠券核销流水表：记录订单锁券、支付成功确认核销、取消订单释放优惠券等操作';

COMMENT ON COLUMN coupon_usage_record.id IS '优惠券核销流水 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID';
COMMENT ON COLUMN coupon_usage_record.user_coupon_id IS '用户优惠券 ID，对应 user_coupon.id，不使用物理外键';
COMMENT ON COLUMN coupon_usage_record.coupon_template_id IS '优惠券模板 ID，对应 coupon_template.id，不使用物理外键';
COMMENT ON COLUMN coupon_usage_record.user_id IS '用户 ID，对应 user_profile.id，不使用物理外键';
COMMENT ON COLUMN coupon_usage_record.order_id IS '订单 ID，当前按 BIGINT 保存';
COMMENT ON COLUMN coupon_usage_record.action IS '核销动作：LOCK 锁定，CONFIRM 确认使用，RELEASE 释放';
COMMENT ON COLUMN coupon_usage_record.order_amount_yuan IS '订单金额，单位：元';
COMMENT ON COLUMN coupon_usage_record.discount_amount_yuan IS '优惠金额，单位：元';
COMMENT ON COLUMN coupon_usage_record.idempotency_key IS '幂等键，用于防止同一个订单动作重复执行';
COMMENT ON COLUMN coupon_usage_record.created_at IS '创建时间';
