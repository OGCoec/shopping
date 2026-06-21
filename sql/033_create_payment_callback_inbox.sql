-- ============================================
-- 文件名：033_create_payment_callback_inbox.sql
-- 说明：创建支付成功回调可靠收件箱表
-- 约定：
-- 1. 一行代表一次第三方支付成功回调事件；
-- 2. 回调接口只写入本表并快速返回，后续由 RabbitMQ 和 5 秒调度器批量处理；
-- 3. idempotency_key 用于防止同一订单、同一外部支付流水重复入队；
-- 4. 本表是支付回调可靠队列，RabbitMQ 只做加速触发。
-- ============================================

CREATE TABLE IF NOT EXISTS payment_callback_inbox (
    -- 数据库支付回调收件箱主键，只用于数据库内部索引和排序
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 支付回调流水号，用于后台查询和日志追踪
    callback_no VARCHAR(64) NOT NULL,

    -- 商户订单号，对应 trade_order.order_no
    order_no VARCHAR(64) NOT NULL,

    -- 第三方支付订单号，例如支付平台回调中的 trade_no
    external_trade_no VARCHAR(128),

    -- 支付提供方：SIMULATED、XARR、WECHAT、ALIPAY 等
    payment_provider VARCHAR(32) NOT NULL DEFAULT 'SIMULATED',

    -- 第三方确认支付成功的时间
    paid_at TIMESTAMPTZ NOT NULL,

    -- 用户实际支付金额，单位：元；订单不存在时必须依赖该字段创建退款单
    paid_amount_yuan NUMERIC(12,2),

    -- 回调处理状态：RECEIVED 待处理，PROCESSING 处理中，PROCESSED 已处理，FAILED 处理失败可重试
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',

    -- 回调处理重试次数
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- 下一次允许调度重试的时间
    next_retry_at TIMESTAMPTZ,

    -- 处理结果：PAID、PAID_IDEMPOTENT、REFUND_PENDING、FAILED
    result_outcome VARCHAR(32),

    -- 处理时观察到的订单状态
    result_order_status VARCHAR(32),

    -- 异常支付创建或命中的退款单号
    refund_no VARCHAR(64),

    -- 最近一次处理失败错误码
    last_error_code VARCHAR(64),

    -- 最近一次处理失败错误信息
    last_error_message TEXT,

    -- 回调幂等键，防止同一支付流水重复入队
    idempotency_key VARCHAR(160) NOT NULL,

    -- 原始回调请求体快照
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 数据版本号，用于并发处理控制
    version BIGINT NOT NULL DEFAULT 1,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_payment_callback_inbox_callback_no
        UNIQUE (callback_no),

    CONSTRAINT uq_payment_callback_inbox_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT ck_payment_callback_inbox_callback_no_not_blank
        CHECK (btrim(callback_no) <> ''),

    CONSTRAINT ck_payment_callback_inbox_order_no_not_blank
        CHECK (btrim(order_no) <> ''),

    CONSTRAINT ck_payment_callback_inbox_external_trade_no_not_blank
        CHECK (external_trade_no IS NULL OR btrim(external_trade_no) <> ''),

    CONSTRAINT ck_payment_callback_inbox_payment_provider_not_blank
        CHECK (btrim(payment_provider) <> ''),

    CONSTRAINT ck_payment_callback_inbox_paid_amount
        CHECK (paid_amount_yuan IS NULL OR paid_amount_yuan >= 0),

    CONSTRAINT ck_payment_callback_inbox_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED')),

    CONSTRAINT ck_payment_callback_inbox_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT ck_payment_callback_inbox_result_outcome
        CHECK (
            result_outcome IS NULL
            OR result_outcome IN ('PAID', 'PAID_IDEMPOTENT', 'REFUND_PENDING', 'FAILED')
        ),

    CONSTRAINT ck_payment_callback_inbox_result_order_status
        CHECK (
            result_order_status IS NULL
            OR result_order_status IN (
                'STOCK_CONFIRMING',
                'PENDING_PAYMENT',
                'CLOSING',
                'PAID',
                'CANCELLED',
                'CLOSED',
                'UNKNOWN',
                'NOT_FOUND'
            )
        ),

    CONSTRAINT ck_payment_callback_inbox_refund_no_not_blank
        CHECK (refund_no IS NULL OR btrim(refund_no) <> ''),

    CONSTRAINT ck_payment_callback_inbox_last_error_code_not_blank
        CHECK (last_error_code IS NULL OR btrim(last_error_code) <> ''),

    CONSTRAINT ck_payment_callback_inbox_idempotency_key_not_blank
        CHECK (btrim(idempotency_key) <> ''),

    CONSTRAINT ck_payment_callback_inbox_raw_payload_json_object
        CHECK (jsonb_typeof(raw_payload_json) = 'object'),

    CONSTRAINT ck_payment_callback_inbox_version
        CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_callback_inbox_dispatch
    ON payment_callback_inbox (status, next_retry_at, created_at, id)
    WHERE status IN ('RECEIVED', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_payment_callback_inbox_created_id
    ON payment_callback_inbox (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_payment_callback_inbox_order_no
    ON payment_callback_inbox (order_no, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_payment_callback_inbox_external_trade_no
    ON payment_callback_inbox (external_trade_no)
    WHERE external_trade_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_callback_inbox_refund_no
    ON payment_callback_inbox (refund_no)
    WHERE refund_no IS NOT NULL;

COMMENT ON TABLE payment_callback_inbox IS '支付成功回调可靠收件箱：记录第三方支付成功回调，接口只入队，后续由批处理统一改订单状态或创建退款单';

COMMENT ON COLUMN payment_callback_inbox.id IS '数据库支付回调收件箱主键，只用于数据库内部索引和排序';
COMMENT ON COLUMN payment_callback_inbox.callback_no IS '支付回调流水号，用于后台查询和日志追踪';
COMMENT ON COLUMN payment_callback_inbox.order_no IS '商户订单号，对应 trade_order.order_no';
COMMENT ON COLUMN payment_callback_inbox.external_trade_no IS '第三方支付订单号，例如支付平台回调中的 trade_no';
COMMENT ON COLUMN payment_callback_inbox.payment_provider IS '支付提供方：SIMULATED、XARR、WECHAT、ALIPAY 等';
COMMENT ON COLUMN payment_callback_inbox.paid_at IS '第三方确认支付成功的时间';
COMMENT ON COLUMN payment_callback_inbox.paid_amount_yuan IS '用户实际支付金额，单位：元；订单不存在时必须依赖该字段创建退款单';
COMMENT ON COLUMN payment_callback_inbox.status IS '回调处理状态：RECEIVED 待处理，PROCESSING 处理中，PROCESSED 已处理，FAILED 处理失败可重试';
COMMENT ON COLUMN payment_callback_inbox.retry_count IS '回调处理重试次数';
COMMENT ON COLUMN payment_callback_inbox.next_retry_at IS '下一次允许调度重试的时间';
COMMENT ON COLUMN payment_callback_inbox.result_outcome IS '处理结果：PAID 已支付，PAID_IDEMPOTENT 重复已支付，REFUND_PENDING 已创建或命中退款单，FAILED 处理失败';
COMMENT ON COLUMN payment_callback_inbox.result_order_status IS '处理时观察到的订单状态';
COMMENT ON COLUMN payment_callback_inbox.refund_no IS '异常支付创建或命中的退款单号';
COMMENT ON COLUMN payment_callback_inbox.last_error_code IS '最近一次处理失败错误码';
COMMENT ON COLUMN payment_callback_inbox.last_error_message IS '最近一次处理失败错误信息';
COMMENT ON COLUMN payment_callback_inbox.idempotency_key IS '回调幂等键，防止同一订单、同一外部支付流水重复入队';
COMMENT ON COLUMN payment_callback_inbox.raw_payload_json IS '原始回调请求体快照';
COMMENT ON COLUMN payment_callback_inbox.version IS '数据版本号，用于并发处理控制';
COMMENT ON COLUMN payment_callback_inbox.created_at IS '创建时间';
COMMENT ON COLUMN payment_callback_inbox.updated_at IS '更新时间';
