-- ============================================
-- 文件名：033_view_payment_callback_inbox.sql
-- 说明：payment_callback_inbox 只读视图，展示支付回调收件箱核心字段
-- 约定：视图仅用于只读查看，不参与业务写入；回调处理以 payment_callback_inbox 主表为准
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_payment_callback_inbox AS
SELECT
    id,
    callback_no,
    order_no,
    external_trade_no,
    payment_provider,
    paid_at,
    paid_amount_yuan,
    status,
    retry_count,
    next_retry_at,
    result_outcome,
    result_order_status,
    refund_no,
    last_error_code,
    last_error_message,
    idempotency_key,
    raw_payload_json,
    version,
    created_at,
    updated_at
FROM payment_callback_inbox;

COMMENT ON VIEW v_payment_callback_inbox IS '支付成功回调可靠收件箱只读视图：展示回调流水、订单号、支付流水、处理状态、处理结果、退款单号和原始回调快照';

COMMENT ON COLUMN v_payment_callback_inbox.id IS '数据库支付回调收件箱主键，只用于数据库内部索引和排序';
COMMENT ON COLUMN v_payment_callback_inbox.callback_no IS '支付回调流水号，用于后台查询和日志追踪';
COMMENT ON COLUMN v_payment_callback_inbox.order_no IS '商户订单号，对应 trade_order.order_no';
COMMENT ON COLUMN v_payment_callback_inbox.external_trade_no IS '第三方支付订单号，例如支付平台回调中的 trade_no';
COMMENT ON COLUMN v_payment_callback_inbox.payment_provider IS '支付提供方：SIMULATED、XARR、WECHAT、ALIPAY 等';
COMMENT ON COLUMN v_payment_callback_inbox.paid_at IS '第三方确认支付成功的时间';
COMMENT ON COLUMN v_payment_callback_inbox.paid_amount_yuan IS '用户实际支付金额，单位：元';
COMMENT ON COLUMN v_payment_callback_inbox.status IS '回调处理状态：RECEIVED 待处理，PROCESSING 处理中，PROCESSED 已处理，FAILED 处理失败可重试';
COMMENT ON COLUMN v_payment_callback_inbox.retry_count IS '回调处理重试次数';
COMMENT ON COLUMN v_payment_callback_inbox.next_retry_at IS '下一次允许调度重试的时间';
COMMENT ON COLUMN v_payment_callback_inbox.result_outcome IS '处理结果：PAID 已支付，PAID_IDEMPOTENT 重复已支付，REFUND_PENDING 已创建或命中退款单，FAILED 处理失败';
COMMENT ON COLUMN v_payment_callback_inbox.result_order_status IS '处理时观察到的订单状态';
COMMENT ON COLUMN v_payment_callback_inbox.refund_no IS '异常支付创建或命中的退款单号';
COMMENT ON COLUMN v_payment_callback_inbox.last_error_code IS '最近一次处理失败错误码';
COMMENT ON COLUMN v_payment_callback_inbox.last_error_message IS '最近一次处理失败错误信息';
COMMENT ON COLUMN v_payment_callback_inbox.idempotency_key IS '回调幂等键，防止同一订单、同一外部支付流水重复入队';
COMMENT ON COLUMN v_payment_callback_inbox.raw_payload_json IS '原始回调请求体快照';
COMMENT ON COLUMN v_payment_callback_inbox.version IS '数据版本号，用于并发处理控制';
COMMENT ON COLUMN v_payment_callback_inbox.created_at IS '创建时间';
COMMENT ON COLUMN v_payment_callback_inbox.updated_at IS '更新时间';
