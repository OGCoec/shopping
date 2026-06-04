-- ============================================
-- 文件名：032_create_payment_refund_record.sql
-- 说明：创建支付退款流水表
-- 约定：
-- 1. 一行代表一次退款处理链路，包括自动检测、用户申请、管理员创建或支付回调触发；
-- 2. refund_no 使用业务退款单号，用于用户前台、管理员后台、客服和对账查询；
-- 3. 本表不设置 trade_order 物理外键，因为需要覆盖“支付成功但订单不存在”的异常兜底场景；
-- 4. 不调用外部退款 API 时，系统只记录退款工单、处理状态、管理员确认和退款凭证；
-- 5. idempotency_key 用于防止同一订单、同一支付流水、同一异常原因重复创建退款单。
-- ============================================

CREATE TABLE IF NOT EXISTS payment_refund_record (
    -- 数据库退款流水主键，只用于数据库内部索引和排序
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 退款单号，用于用户前台、管理员后台、客服和对账查询
    refund_no VARCHAR(64) NOT NULL,

    -- 关联订单号，对应 trade_order.order_no；订单不存在时保留支付回调里的商户订单号
    order_no VARCHAR(64) NOT NULL,

    -- 用户 ID；订单不存在或无法识别用户时可为空
    user_id BIGINT,

    -- 支付提供方：SIMULATED 模拟支付，XARR 乐风码付，WECHAT 微信，ALIPAY 支付宝等
    payment_provider VARCHAR(32) NOT NULL DEFAULT 'SIMULATED',

    -- 第三方支付订单号，例如支付平台回调中的 trade_no
    external_trade_no VARCHAR(128),

    -- 外部退款单号，后续接真实退款接口时记录第三方退款流水
    external_refund_no VARCHAR(128),

    -- 支付回调流水标识，后续如有支付回调日志表可用于关联原始回调
    payment_callback_id VARCHAR(128),

    -- 用户实际支付金额，单位：元
    paid_amount_yuan NUMERIC(12,2) NOT NULL,

    -- 应退款金额，单位：元
    refund_amount_yuan NUMERIC(12,2) NOT NULL,

    -- 币种，默认人民币
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',

    -- 退款处理状态
    status VARCHAR(32) NOT NULL DEFAULT 'REFUND_PENDING',

    -- 退款来源
    source VARCHAR(32) NOT NULL,

    -- 退款原因编码
    reason_code VARCHAR(64) NOT NULL,

    -- 退款原因详细说明
    reason_detail TEXT,

    -- 创建退款单时观察到的订单状态
    order_status_when_detected VARCHAR(32),

    -- 自动检测发现异常的时间
    detected_at TIMESTAMPTZ,

    -- 自动检测批次号
    detection_batch_no VARCHAR(64),

    -- 审核通过该退款单的管理员 ID
    approved_admin_id BIGINT,

    -- 管理员审核通过时间
    approved_at TIMESTAMPTZ,

    -- 拒绝该退款单的管理员 ID
    rejected_admin_id BIGINT,

    -- 管理员拒绝时间
    rejected_at TIMESTAMPTZ,

    -- 管理员拒绝退款原因
    reject_reason TEXT,

    -- 确认已经完成原路退款的管理员 ID
    refunded_admin_id BIGINT,

    -- 管理员确认已退款时间
    refunded_at TIMESTAMPTZ,

    -- 自动退款开始处理时间
    refund_started_at TIMESTAMPTZ,

    -- 自动退款重试次数
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- 下一次允许自动重试的时间
    next_retry_at TIMESTAMPTZ,

    -- 最近一次自动退款失败错误码
    last_error_code VARCHAR(64),

    -- 最近一次自动退款失败错误信息
    last_error_message TEXT,

    -- 退款凭证号，例如微信、支付宝、银行或人工处理凭证编号
    refund_proof_no VARCHAR(128),

    -- 退款凭证图片或文件地址
    refund_proof_url TEXT,

    -- 管理员处理备注
    admin_remark TEXT,

    -- 展示给用户的退款说明
    user_message TEXT,

    -- 创建退款单时的上下文快照
    snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 扩展字段，保留后续外部退款接口、风控信息或人工处理补充数据
    extra_json JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 退款幂等键，防止重复创建退款单
    idempotency_key VARCHAR(160) NOT NULL,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 数据版本号，用于管理员并发审核、确认退款时做乐观锁控制
    version BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT uq_payment_refund_record_refund_no
        UNIQUE (refund_no),

    CONSTRAINT uq_payment_refund_record_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT ck_payment_refund_record_refund_no_not_blank
        CHECK (btrim(refund_no) <> ''),

    CONSTRAINT ck_payment_refund_record_order_no_not_blank
        CHECK (btrim(order_no) <> ''),

    CONSTRAINT ck_payment_refund_record_user_id
        CHECK (user_id IS NULL OR user_id > 0),

    CONSTRAINT ck_payment_refund_record_payment_provider_not_blank
        CHECK (btrim(payment_provider) <> ''),

    CONSTRAINT ck_payment_refund_record_external_trade_no_not_blank
        CHECK (external_trade_no IS NULL OR btrim(external_trade_no) <> ''),

    CONSTRAINT ck_payment_refund_record_payment_callback_id_not_blank
        CHECK (payment_callback_id IS NULL OR btrim(payment_callback_id) <> ''),

    CONSTRAINT ck_payment_refund_record_amount
        CHECK (
            paid_amount_yuan >= 0
            AND refund_amount_yuan >= 0
            AND refund_amount_yuan <= paid_amount_yuan
        ),

    CONSTRAINT ck_payment_refund_record_currency_not_blank
        CHECK (btrim(currency) <> ''),

    CONSTRAINT ck_payment_refund_record_status
        CHECK (status IN (
            'REFUND_PENDING',
            'REFUNDING',
            'REFUND_APPROVED',
            'REFUND_REJECTED',
            'REFUNDED',
            'REFUND_FAILED',
            'REFUND_CANCELLED'
        )),

    CONSTRAINT ck_payment_refund_record_source
        CHECK (source IN (
            'AUTO_DETECTED',
            'PAYMENT_CALLBACK',
            'USER_APPLY',
            'ADMIN_CREATE'
        )),

    CONSTRAINT ck_payment_refund_record_reason_code
        CHECK (reason_code IN (
            'PAID_AFTER_ORDER_CLOSED',
            'PAID_AFTER_ORDER_CANCELLED',
            'ORDER_NOT_FOUND_AFTER_PAID',
            'FULFILLMENT_FAILED',
            'USER_NOT_RECEIVED_GOODS',
            'DUPLICATE_PAYMENT',
            'ADMIN_MANUAL',
            'OTHER'
        )),

    CONSTRAINT ck_payment_refund_record_order_status_when_detected
        CHECK (
            order_status_when_detected IS NULL
            OR order_status_when_detected IN (
                'PENDING_PAYMENT',
                'CLOSING',
                'PAID',
                'CANCELLED',
                'CLOSED',
                'UNKNOWN',
                'NOT_FOUND'
            )
        ),

    CONSTRAINT ck_payment_refund_record_detection_batch_no_not_blank
        CHECK (detection_batch_no IS NULL OR btrim(detection_batch_no) <> ''),

    CONSTRAINT ck_payment_refund_record_admin_ids
        CHECK (
            (approved_admin_id IS NULL OR approved_admin_id > 0)
            AND (rejected_admin_id IS NULL OR rejected_admin_id > 0)
            AND (refunded_admin_id IS NULL OR refunded_admin_id > 0)
        ),

    CONSTRAINT ck_payment_refund_record_refund_proof_no_not_blank
        CHECK (refund_proof_no IS NULL OR btrim(refund_proof_no) <> ''),

    CONSTRAINT ck_payment_refund_record_external_refund_no_not_blank
        CHECK (external_refund_no IS NULL OR btrim(external_refund_no) <> ''),

    CONSTRAINT ck_payment_refund_record_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT ck_payment_refund_record_last_error_code_not_blank
        CHECK (last_error_code IS NULL OR btrim(last_error_code) <> ''),

    CONSTRAINT ck_payment_refund_record_snapshot_json_object
        CHECK (jsonb_typeof(snapshot_json) = 'object'),

    CONSTRAINT ck_payment_refund_record_extra_json_object
        CHECK (jsonb_typeof(extra_json) = 'object'),

    CONSTRAINT ck_payment_refund_record_idempotency_key_not_blank
        CHECK (btrim(idempotency_key) <> ''),

    CONSTRAINT ck_payment_refund_record_version
        CHECK (version > 0)
);

ALTER TABLE payment_refund_record
    ADD COLUMN IF NOT EXISTS external_refund_no VARCHAR(128),
    ADD COLUMN IF NOT EXISTS refund_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS last_error_message TEXT;

ALTER TABLE payment_refund_record
    DROP CONSTRAINT IF EXISTS ck_payment_refund_record_status,
    DROP CONSTRAINT IF EXISTS ck_payment_refund_record_external_refund_no_not_blank,
    DROP CONSTRAINT IF EXISTS ck_payment_refund_record_retry_count,
    DROP CONSTRAINT IF EXISTS ck_payment_refund_record_last_error_code_not_blank;

ALTER TABLE payment_refund_record
    ADD CONSTRAINT ck_payment_refund_record_status
        CHECK (status IN (
            'REFUND_PENDING',
            'REFUNDING',
            'REFUND_APPROVED',
            'REFUND_REJECTED',
            'REFUNDED',
            'REFUND_FAILED',
            'REFUND_CANCELLED'
        )),
    ADD CONSTRAINT ck_payment_refund_record_external_refund_no_not_blank
        CHECK (external_refund_no IS NULL OR btrim(external_refund_no) <> ''),
    ADD CONSTRAINT ck_payment_refund_record_retry_count
        CHECK (retry_count >= 0),
    ADD CONSTRAINT ck_payment_refund_record_last_error_code_not_blank
        CHECK (last_error_code IS NULL OR btrim(last_error_code) <> '');

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_refund_record_order_external_trade
    ON payment_refund_record (order_no, external_trade_no)
    WHERE external_trade_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_refund_record_status_created
    ON payment_refund_record (status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_payment_refund_record_order_no
    ON payment_refund_record (order_no);

CREATE INDEX IF NOT EXISTS idx_payment_refund_record_user_created
    ON payment_refund_record (user_id, created_at DESC, id DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_refund_record_external_trade_no
    ON payment_refund_record (external_trade_no)
    WHERE external_trade_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_refund_record_detection_batch_no
    ON payment_refund_record (detection_batch_no)
    WHERE detection_batch_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_refund_record_next_retry
    ON payment_refund_record (status, next_retry_at, created_at, id)
    WHERE status IN ('REFUND_PENDING', 'REFUND_FAILED');

COMMENT ON TABLE payment_refund_record IS '支付退款流水表：记录支付成功但订单关闭、订单取消、订单不存在、商品履约失败、用户未收到商品等需要退款的兜底流程。该表只记录退款处理链路，不直接代表第三方已经真实打款。';

COMMENT ON COLUMN payment_refund_record.id IS '数据库退款流水主键，使用 PostgreSQL 自增 BIGINT，只用于数据库内部索引和排序';
COMMENT ON COLUMN payment_refund_record.refund_no IS '退款单号，建议使用 HybridSemaphoreIdWorker 生成的 Base62 编码，用于管理员后台、用户前台、客服和对账查询';
COMMENT ON COLUMN payment_refund_record.order_no IS '关联订单号，对应 trade_order.order_no；即使订单不存在，也保存支付回调中的商户订单号用于追踪';
COMMENT ON COLUMN payment_refund_record.user_id IS '用户 ID，对应 user_profile.id；订单不存在或无法识别用户时可为空';
COMMENT ON COLUMN payment_refund_record.payment_provider IS '支付提供方：SIMULATED 模拟支付，XARR 乐风码付，WECHAT 微信，ALIPAY 支付宝等';
COMMENT ON COLUMN payment_refund_record.external_trade_no IS '第三方支付订单号，例如支付平台回调中的 trade_no';
COMMENT ON COLUMN payment_refund_record.external_refund_no IS '外部退款单号，后续接真实退款接口时记录第三方退款流水';
COMMENT ON COLUMN payment_refund_record.payment_callback_id IS '支付回调流水标识，如果后续有支付回调日志表，可用于关联原始回调';
COMMENT ON COLUMN payment_refund_record.paid_amount_yuan IS '用户实际支付金额，单位：元，来自支付成功记录或支付回调';
COMMENT ON COLUMN payment_refund_record.refund_amount_yuan IS '应退款金额，单位：元；不允许超过实际支付金额';
COMMENT ON COLUMN payment_refund_record.currency IS '币种，默认 CNY';
COMMENT ON COLUMN payment_refund_record.status IS '退款状态：REFUND_PENDING 待处理，REFUNDING 自动退款处理中，REFUND_APPROVED 已审核，REFUND_REJECTED 已拒绝，REFUNDED 已退款，REFUND_FAILED 退款异常，REFUND_CANCELLED 已取消';
COMMENT ON COLUMN payment_refund_record.source IS '退款来源：AUTO_DETECTED 定时任务自动检测，PAYMENT_CALLBACK 支付回调触发，USER_APPLY 用户申请，ADMIN_CREATE 管理员创建';
COMMENT ON COLUMN payment_refund_record.reason_code IS '退款原因编码：支付成功但订单关闭、订单取消、订单不存在、履约失败、用户未收到商品、重复支付、管理员手动或其他';
COMMENT ON COLUMN payment_refund_record.reason_detail IS '退款原因详细说明，用于管理员审核和客服排查';
COMMENT ON COLUMN payment_refund_record.order_status_when_detected IS '自动检测或回调触发退款时观察到的订单状态';
COMMENT ON COLUMN payment_refund_record.detected_at IS '自动检测发现异常的时间';
COMMENT ON COLUMN payment_refund_record.detection_batch_no IS '自动检测批次号，用于定位某一次定时任务扫描产生的退款单';
COMMENT ON COLUMN payment_refund_record.approved_admin_id IS '审核通过该退款单的管理员 ID';
COMMENT ON COLUMN payment_refund_record.approved_at IS '管理员审核通过时间';
COMMENT ON COLUMN payment_refund_record.rejected_admin_id IS '拒绝该退款单的管理员 ID';
COMMENT ON COLUMN payment_refund_record.rejected_at IS '管理员拒绝时间';
COMMENT ON COLUMN payment_refund_record.reject_reason IS '管理员拒绝退款原因';
COMMENT ON COLUMN payment_refund_record.refunded_admin_id IS '确认已经完成原路退款的管理员 ID';
COMMENT ON COLUMN payment_refund_record.refunded_at IS '管理员确认已退款时间';
COMMENT ON COLUMN payment_refund_record.refund_started_at IS '自动退款开始处理时间';
COMMENT ON COLUMN payment_refund_record.retry_count IS '自动退款重试次数';
COMMENT ON COLUMN payment_refund_record.next_retry_at IS '下一次允许自动重试的时间';
COMMENT ON COLUMN payment_refund_record.last_error_code IS '最近一次自动退款失败错误码';
COMMENT ON COLUMN payment_refund_record.last_error_message IS '最近一次自动退款失败错误信息';
COMMENT ON COLUMN payment_refund_record.refund_proof_no IS '退款凭证号，例如微信、支付宝、银行或人工处理凭证编号';
COMMENT ON COLUMN payment_refund_record.refund_proof_url IS '退款凭证图片或文件地址，可为空';
COMMENT ON COLUMN payment_refund_record.admin_remark IS '管理员处理备注';
COMMENT ON COLUMN payment_refund_record.user_message IS '展示给用户的退款说明';
COMMENT ON COLUMN payment_refund_record.snapshot_json IS '创建退款单时的上下文快照，例如订单状态、支付金额、商品履约状态、回调参数摘要';
COMMENT ON COLUMN payment_refund_record.extra_json IS '扩展字段，保留后续接入外部退款接口、风控信息或人工处理补充数据';
COMMENT ON COLUMN payment_refund_record.idempotency_key IS '退款幂等键，用于防止同一订单、同一支付流水或同一异常场景重复创建退款单';
COMMENT ON COLUMN payment_refund_record.created_at IS '创建时间';
COMMENT ON COLUMN payment_refund_record.updated_at IS '更新时间';
COMMENT ON COLUMN payment_refund_record.version IS '数据版本号，用于管理员并发审核、确认退款时做乐观锁控制';
