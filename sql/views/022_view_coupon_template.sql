-- ============================================
-- 文件名：022_view_coupon_template.sql
-- 说明：coupon_template 可读视图，将 16 字节 BYTEA 主键转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：视图仅用于只读查看，不参与业务写入；原表读写仍走 BYTEA 主键
-- 适配：PostgreSQL
-- ============================================

CREATE OR REPLACE VIEW v_coupon_template AS
SELECT
    to_base62(id)     AS id_base62,   -- 与后端、接口一致的 Base62 主键
    encode(id, 'hex') AS id_hex,      -- 兜底 Hex 展示
    coupon_code,
    name,
    discount_type,
    threshold_amount_yuan,
    discount_amount_yuan,
    discount_rate,
    max_discount_amount_yuan,
    total_quantity,
    remaining_quantity,
    per_user_limit,
    scope_type,
    receive_start_at,
    receive_end_at,
    valid_start_at,
    valid_end_at,
    status,
    version,
    created_at,
    updated_at
FROM coupon_template;

COMMENT ON VIEW v_coupon_template IS '优惠券模板可读视图：主键 id 以 Base62/Hex 展示';
COMMENT ON COLUMN v_coupon_template.id_base62 IS '优惠券模板 ID 的 Base62 可读形式，与后端、接口一致';
COMMENT ON COLUMN v_coupon_template.id_hex IS '优惠券模板 ID 的 Hex 可读形式';
COMMENT ON COLUMN v_coupon_template.coupon_code IS '优惠券编码，用于后台识别、幂等导入或运营配置';
COMMENT ON COLUMN v_coupon_template.name IS '优惠券名称，用于前台展示和后台管理';
COMMENT ON COLUMN v_coupon_template.discount_type IS '优惠类型：AMOUNT 固定金额减免，PERCENT 按比例折扣';
COMMENT ON COLUMN v_coupon_template.threshold_amount_yuan IS '最低使用金额，单位：元，0 表示无门槛';
COMMENT ON COLUMN v_coupon_template.discount_amount_yuan IS '固定减免金额，单位：元，discount_type 为 AMOUNT 时使用';
COMMENT ON COLUMN v_coupon_template.discount_rate IS '折扣比例，discount_type 为 PERCENT 时使用，例如 0.8000 表示 8 折';
COMMENT ON COLUMN v_coupon_template.max_discount_amount_yuan IS '折扣券最高减免金额，单位：元，可为空表示不封顶';
COMMENT ON COLUMN v_coupon_template.total_quantity IS '优惠券发放总数量';
COMMENT ON COLUMN v_coupon_template.remaining_quantity IS '优惠券剩余可领取数量';
COMMENT ON COLUMN v_coupon_template.per_user_limit IS '单个用户最多可领取数量';
COMMENT ON COLUMN v_coupon_template.scope_type IS '适用范围类型：ALL 全场，CATEGORY 分类，SPU 商品，SKU 具体规格';
COMMENT ON COLUMN v_coupon_template.receive_start_at IS '可领取开始时间';
COMMENT ON COLUMN v_coupon_template.receive_end_at IS '可领取结束时间';
COMMENT ON COLUMN v_coupon_template.valid_start_at IS '优惠券有效期开始时间';
COMMENT ON COLUMN v_coupon_template.valid_end_at IS '优惠券有效期结束时间';
COMMENT ON COLUMN v_coupon_template.status IS '模板状态：DRAFT 草稿，ACTIVE 启用，DISABLED 禁用，EXPIRED 已过期，DELETED 已删除';
COMMENT ON COLUMN v_coupon_template.version IS '数据版本号，用于后续并发更新库存或状态时做乐观锁';
COMMENT ON COLUMN v_coupon_template.created_at IS '创建时间';
COMMENT ON COLUMN v_coupon_template.updated_at IS '更新时间';