-- ============================================
-- 文件名：034_view_card_secret_inventory.sql
-- 说明：card_secret_inventory 可读视图，将 id、sku_id 转为 Base62 / Hex 展示
-- 依赖：000_base62_functions.sql 中的 to_base62 函数
-- 约定：
-- 1. 视图仅用于只读查看，不参与业务写入；
-- 2. 原表读写仍以 BYTEA sku_id 为准；
-- 3. 出于安全原因，视图不展示 secret_ciphertext 和 secret_nonce，避免运维查询时泄漏可解密材料。
-- 适配：PostgreSQL
-- ============================================

DROP VIEW IF EXISTS v_card_secret_inventory;

CREATE OR REPLACE VIEW v_card_secret_inventory AS
SELECT
    to_base62(id)         AS id_base62,
    encode(id, 'hex')     AS id_hex,
    to_base62(sku_id)     AS sku_id_base62,
    encode(sku_id, 'hex') AS sku_id_hex,
    batch_no,
    secret_hash,
    secret_key_version,
    encrypt_algorithm,
    hash_algorithm,
    status,
    order_no,
    user_id,
    sold_at,
    disabled_at,
    remark,
    import_source,
    created_by_admin_username,
    created_by_admin_email,
    created_by_admin_phone,
    version,
    created_at,
    updated_at
FROM card_secret_inventory;

COMMENT ON COLUMN v_card_secret_inventory.import_source IS '卡密导入来源：TEXT_INPUT 手动输入，TXT_FILE 文本文件导入，MIXED 手动输入和文件混合导入';
COMMENT ON COLUMN v_card_secret_inventory.created_by_admin_username IS '导入该卡密的管理员用户名，用于后台按自己导入的卡密过滤查询';
COMMENT ON COLUMN v_card_secret_inventory.created_by_admin_email IS '导入该卡密的管理员邮箱';
COMMENT ON COLUMN v_card_secret_inventory.created_by_admin_phone IS '导入该卡密的管理员手机号';

COMMENT ON VIEW v_card_secret_inventory IS '卡密库存可读视图：展示卡密库存主键和 SKU 的 Base62/Hex、批次、摘要、状态和发放归属；不展示卡密密文和 nonce。';

COMMENT ON COLUMN v_card_secret_inventory.id_base62 IS '卡密库存主键 id 的 Base62 可读形式，由 16 字节 Hybrid ID 转出，用于接口展示和后台查询';
COMMENT ON COLUMN v_card_secret_inventory.id_hex IS '卡密库存主键 id 的 Hex 可读形式，用于数据库排查和兜底核对';
COMMENT ON COLUMN v_card_secret_inventory.sku_id_base62 IS '所属商品 SKU ID 的 Base62 可读形式，对应 product_sku.id';
COMMENT ON COLUMN v_card_secret_inventory.sku_id_hex IS '所属商品 SKU ID 的 Hex 可读形式，对应 product_sku.id';
COMMENT ON COLUMN v_card_secret_inventory.batch_no IS '导入批次号，用于追踪某一次卡密文本文件导入';
COMMENT ON COLUMN v_card_secret_inventory.secret_hash IS '卡密明文的 HMAC-SHA256 摘要，用于重复导入检测；不可逆，不能用于还原卡密明文';
COMMENT ON COLUMN v_card_secret_inventory.secret_key_version IS '加密和摘要使用的密钥版本，只记录版本号，不保存真实密钥';
COMMENT ON COLUMN v_card_secret_inventory.encrypt_algorithm IS '加密算法标识，当前只允许 AES_256_GCM';
COMMENT ON COLUMN v_card_secret_inventory.hash_algorithm IS '摘要算法标识，当前只允许 HMAC_SHA256';
COMMENT ON COLUMN v_card_secret_inventory.status IS '卡密状态：UNUSED 未发放，SOLD 已发放，DISABLED 已禁用';
COMMENT ON COLUMN v_card_secret_inventory.order_no IS '发放后关联的订单号，对应 trade_order.order_no；逻辑外键，不创建物理外键';
COMMENT ON COLUMN v_card_secret_inventory.user_id IS '发放后关联的用户 ID，对应 user_profile.id；逻辑外键，不创建物理外键';
COMMENT ON COLUMN v_card_secret_inventory.sold_at IS '卡密发放时间';
COMMENT ON COLUMN v_card_secret_inventory.disabled_at IS '卡密禁用时间';
COMMENT ON COLUMN v_card_secret_inventory.remark IS '管理员备注或导入备注';
COMMENT ON COLUMN v_card_secret_inventory.version IS '数据版本号，用于并发发放、禁用或后台修改时做乐观锁控制';
COMMENT ON COLUMN v_card_secret_inventory.created_at IS '创建时间';
COMMENT ON COLUMN v_card_secret_inventory.updated_at IS '更新时间';
