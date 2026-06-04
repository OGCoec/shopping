-- ============================================
-- 文件名：032_view_payment_refund_record.sql
-- 说明：payment_refund_record 可读视图，展示退款流水核心字段
-- 约定：视图仅用于只读查看，不参与业务写入；退款处理以 payment_refund_record 为准
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_payment_refund_record AS
SELECT
    id,
    refund_no,
    order_no,
    user_id,
    payment_provider,
    external_trade_no,
    external_refund_no,
    payment_callback_id,
    paid_amount_yuan,
    refund_amount_yuan,
    currency,
    status,
    source,
    reason_code,
    reason_detail,
    order_status_when_detected,
    detected_at,
    detection_batch_no,
    approved_admin_id,
    approved_at,
    rejected_admin_id,
    rejected_at,
    reject_reason,
    refunded_admin_id,
    refunded_at,
    refund_started_at,
    retry_count,
    next_retry_at,
    last_error_code,
    last_error_message,
    refund_proof_no,
    refund_proof_url,
    admin_remark,
    user_message,
    snapshot_json,
    extra_json,
    idempotency_key,
    version,
    created_at,
    updated_at
FROM payment_refund_record;

COMMENT ON VIEW v_payment_refund_record IS '支付退款流水可读视图：展示退款单、订单号、支付流水、退款金额、退款状态、来源、原因、管理员处理信息和上下文快照';

COMMENT ON COLUMN v_payment_refund_record.id IS '数据库退款流水主键，使用 PostgreSQL 自增 BIGINT，只用于数据库内部索引和排序';
COMMENT ON COLUMN v_payment_refund_record.refund_no IS '退款单号，用于管理员后台、用户前台、客服和对账查询';
COMMENT ON COLUMN v_payment_refund_record.order_no IS '关联订单号，对应 trade_order.order_no；订单不存在时保留支付回调中的商户订单号';
COMMENT ON COLUMN v_payment_refund_record.user_id IS '用户 ID，对应 user_profile.id；订单不存在或无法识别用户时可为空';
COMMENT ON COLUMN v_payment_refund_record.payment_provider IS '支付提供方：SIMULATED 模拟支付，XARR 乐风码付，WECHAT 微信，ALIPAY 支付宝等';
COMMENT ON COLUMN v_payment_refund_record.external_trade_no IS '第三方支付订单号，例如支付平台回调中的 trade_no';
COMMENT ON COLUMN v_payment_refund_record.external_refund_no IS '外部退款单号，后续接真实退款接口时记录第三方退款流水';
COMMENT ON COLUMN v_payment_refund_record.payment_callback_id IS '支付回调流水标识，后续如有支付回调日志表可用于关联原始回调';
COMMENT ON COLUMN v_payment_refund_record.paid_amount_yuan IS '用户实际支付金额，单位：元';
COMMENT ON COLUMN v_payment_refund_record.refund_amount_yuan IS '应退款金额，单位：元';
COMMENT ON COLUMN v_payment_refund_record.currency IS '币种，默认 CNY';
COMMENT ON COLUMN v_payment_refund_record.status IS '退款状态：REFUND_PENDING 待处理，REFUNDING 自动退款处理中，REFUND_APPROVED 已审核，REFUND_REJECTED 已拒绝，REFUNDED 已退款，REFUND_FAILED 退款异常，REFUND_CANCELLED 已取消';
COMMENT ON COLUMN v_payment_refund_record.source IS '退款来源：AUTO_DETECTED 定时任务自动检测，PAYMENT_CALLBACK 支付回调触发，USER_APPLY 用户申请，ADMIN_CREATE 管理员创建';
COMMENT ON COLUMN v_payment_refund_record.reason_code IS '退款原因编码：支付成功但订单关闭、订单取消、订单不存在、履约失败、用户未收到商品、重复支付、管理员手动或其他';
COMMENT ON COLUMN v_payment_refund_record.reason_detail IS '退款原因详细说明';
COMMENT ON COLUMN v_payment_refund_record.order_status_when_detected IS '自动检测或回调触发退款时观察到的订单状态';
COMMENT ON COLUMN v_payment_refund_record.detected_at IS '自动检测发现异常的时间';
COMMENT ON COLUMN v_payment_refund_record.detection_batch_no IS '自动检测批次号';
COMMENT ON COLUMN v_payment_refund_record.approved_admin_id IS '审核通过该退款单的管理员 ID';
COMMENT ON COLUMN v_payment_refund_record.approved_at IS '管理员审核通过时间';
COMMENT ON COLUMN v_payment_refund_record.rejected_admin_id IS '拒绝该退款单的管理员 ID';
COMMENT ON COLUMN v_payment_refund_record.rejected_at IS '管理员拒绝时间';
COMMENT ON COLUMN v_payment_refund_record.reject_reason IS '管理员拒绝退款原因';
COMMENT ON COLUMN v_payment_refund_record.refunded_admin_id IS '确认已经完成原路退款的管理员 ID';
COMMENT ON COLUMN v_payment_refund_record.refunded_at IS '管理员确认已退款时间';
COMMENT ON COLUMN v_payment_refund_record.refund_started_at IS '自动退款开始处理时间';
COMMENT ON COLUMN v_payment_refund_record.retry_count IS '自动退款重试次数';
COMMENT ON COLUMN v_payment_refund_record.next_retry_at IS '下一次允许自动重试的时间';
COMMENT ON COLUMN v_payment_refund_record.last_error_code IS '最近一次自动退款失败错误码';
COMMENT ON COLUMN v_payment_refund_record.last_error_message IS '最近一次自动退款失败错误信息';
COMMENT ON COLUMN v_payment_refund_record.refund_proof_no IS '退款凭证号，例如微信、支付宝、银行或人工处理凭证编号';
COMMENT ON COLUMN v_payment_refund_record.refund_proof_url IS '退款凭证图片或文件地址';
COMMENT ON COLUMN v_payment_refund_record.admin_remark IS '管理员处理备注';
COMMENT ON COLUMN v_payment_refund_record.user_message IS '展示给用户的退款说明';
COMMENT ON COLUMN v_payment_refund_record.snapshot_json IS '创建退款单时的上下文快照';
COMMENT ON COLUMN v_payment_refund_record.extra_json IS '扩展字段，保留后续外部退款接口、风控信息或人工处理补充数据';
COMMENT ON COLUMN v_payment_refund_record.idempotency_key IS '退款幂等键，用于防止同一订单、同一支付流水或同一异常场景重复创建退款单';
COMMENT ON COLUMN v_payment_refund_record.version IS '数据版本号，用于管理员并发审核、确认退款时做乐观锁控制';
COMMENT ON COLUMN v_payment_refund_record.created_at IS '创建时间';
COMMENT ON COLUMN v_payment_refund_record.updated_at IS '更新时间';
