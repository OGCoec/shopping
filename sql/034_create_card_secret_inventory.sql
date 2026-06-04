-- ============================================
-- 文件名：034_create_card_secret_inventory.sql
-- 说明：创建卡密库存表
-- 约定：
-- 1. 一行代表一条可发放的卡密库存；
-- 2. sku_id 逻辑关联 product_sku.id，不建立物理外键；
-- 3. 卡密明文不入库，secret_ciphertext 保存 AES-GCM 加密后的 Base64 密文；
-- 4. secret_nonce 保存 AES-GCM nonce/iv 的 Base64 文本，它不是密钥，可以入库；
-- 5. secret_hash 保存 HMAC-SHA256 摘要，用于重复导入检测，不能反推出卡密明文；
-- 6. secret_key_version 只记录密钥版本，不保存真实密钥，真实密钥由应用配置或密钥管理服务提供。
-- ============================================

CREATE TABLE IF NOT EXISTS card_secret_inventory (
    -- 卡密库存主键，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA NOT NULL PRIMARY KEY,

    -- 所属商品 SKU ID，对应 product_sku.id；本字段为逻辑外键，不创建物理外键
    sku_id BYTEA NOT NULL,

    -- 导入批次号，用于追踪某一次卡密文本文件导入
    batch_no VARCHAR(64) NOT NULL,

    -- 加密后的卡密密文，建议保存 AES-256-GCM 输出的 Base64 文本，包含认证标签
    secret_ciphertext TEXT NOT NULL,

    -- 加密使用的 nonce/iv，建议保存 12 字节随机 nonce 的 Base64 文本；它不是密钥
    secret_nonce VARCHAR(64) NOT NULL,

    -- 卡密明文的 HMAC-SHA256 摘要，用于重复导入检测，不可逆，不能用于还原卡密
    secret_hash VARCHAR(128) NOT NULL,

    -- 加密和摘要使用的密钥版本，只记录版本号，不保存真实密钥
    secret_key_version VARCHAR(32) NOT NULL DEFAULT 'v1',

    -- 加密算法标识，当前只允许 AES_256_GCM
    encrypt_algorithm VARCHAR(32) NOT NULL DEFAULT 'AES_256_GCM',

    -- 摘要算法标识，当前只允许 HMAC_SHA256
    hash_algorithm VARCHAR(32) NOT NULL DEFAULT 'HMAC_SHA256',

    -- 卡密状态：UNUSED 未发放，SOLD 已发放，DISABLED 已禁用
    status VARCHAR(32) NOT NULL DEFAULT 'UNUSED',

    -- 发放后关联的订单号，对应 trade_order.order_no；本字段为逻辑外键，不创建物理外键
    order_no VARCHAR(64),

    -- 发放后关联的用户 ID，对应 user_profile.id；本字段为逻辑外键，不创建物理外键
    user_id BIGINT,

    -- 卡密发放时间
    sold_at TIMESTAMPTZ,

    -- 卡密禁用时间
    disabled_at TIMESTAMPTZ,

    -- 管理员备注或导入备注
    remark TEXT,

    -- 卡密导入来源：TEXT_INPUT 手动输入，TXT_FILE 文本文件导入，MIXED 手动输入和文件混合导入
    import_source VARCHAR(32),

    -- 导入该卡密的管理员用户名，用于后台按自己导入的卡密过滤查询
    created_by_admin_username VARCHAR(128),

    -- 导入该卡密的管理员邮箱
    created_by_admin_email VARCHAR(255),

    -- 导入该卡密的管理员手机号
    created_by_admin_phone VARCHAR(64),

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 数据版本号，用于并发发放、禁用或后台修改时做乐观锁控制
    version BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT uq_card_secret_inventory_secret_hash
        UNIQUE (secret_hash),

    CONSTRAINT uq_card_secret_inventory_key_nonce
        UNIQUE (secret_key_version, secret_nonce),

    CONSTRAINT ck_card_secret_inventory_id_hybrid_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_card_secret_inventory_sku_id_hybrid_bytes
        CHECK (octet_length(sku_id) = 16),

    CONSTRAINT ck_card_secret_inventory_batch_no_not_blank
        CHECK (btrim(batch_no) <> ''),

    CONSTRAINT ck_card_secret_inventory_secret_ciphertext_not_blank
        CHECK (btrim(secret_ciphertext) <> ''),

    CONSTRAINT ck_card_secret_inventory_secret_nonce_not_blank
        CHECK (btrim(secret_nonce) <> ''),

    CONSTRAINT ck_card_secret_inventory_secret_hash_not_blank
        CHECK (btrim(secret_hash) <> ''),

    CONSTRAINT ck_card_secret_inventory_secret_key_version_not_blank
        CHECK (btrim(secret_key_version) <> ''),

    CONSTRAINT ck_card_secret_inventory_encrypt_algorithm
        CHECK (encrypt_algorithm IN ('AES_256_GCM')),

    CONSTRAINT ck_card_secret_inventory_hash_algorithm
        CHECK (hash_algorithm IN ('HMAC_SHA256')),

    CONSTRAINT ck_card_secret_inventory_status
        CHECK (status IN ('UNUSED', 'SOLD', 'DISABLED')),

    CONSTRAINT ck_card_secret_inventory_order_no_not_blank
        CHECK (order_no IS NULL OR btrim(order_no) <> ''),

    CONSTRAINT ck_card_secret_inventory_user_id
        CHECK (user_id IS NULL OR user_id > 0),

    CONSTRAINT ck_card_secret_inventory_import_source
        CHECK (
            import_source IS NULL
            OR import_source IN ('TEXT_INPUT', 'TXT_FILE', 'MIXED')
        ),

    CONSTRAINT ck_card_secret_inventory_version
        CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_card_secret_inventory_available
    ON card_secret_inventory (sku_id, id)
    WHERE status = 'UNUSED';

CREATE INDEX IF NOT EXISTS idx_card_secret_inventory_sku_status
    ON card_secret_inventory (sku_id, status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_card_secret_inventory_order_no
    ON card_secret_inventory (order_no)
    WHERE order_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_card_secret_inventory_user_created
    ON card_secret_inventory (user_id, created_at DESC, id DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_card_secret_inventory_batch_no
    ON card_secret_inventory (batch_no, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_card_secret_inventory_import_source
    ON card_secret_inventory (import_source, created_at DESC, id DESC)
    WHERE import_source IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_card_secret_inventory_created_by_admin
    ON card_secret_inventory (created_by_admin_username, created_at DESC, id DESC)
    WHERE created_by_admin_username IS NOT NULL;

COMMENT ON COLUMN card_secret_inventory.import_source IS '卡密导入来源：TEXT_INPUT 手动输入，TXT_FILE 文本文件导入，MIXED 手动输入和文件混合导入';
COMMENT ON COLUMN card_secret_inventory.created_by_admin_username IS '导入该卡密的管理员用户名，用于后台按自己导入的卡密过滤查询';
COMMENT ON COLUMN card_secret_inventory.created_by_admin_email IS '导入该卡密的管理员邮箱';
COMMENT ON COLUMN card_secret_inventory.created_by_admin_phone IS '导入该卡密的管理员手机号';

COMMENT ON TABLE card_secret_inventory IS '卡密库存表：保存商品 SKU 下可发放的卡密密文、去重摘要、导入批次、发放状态和发放归属。本表不保存卡密明文，也不建立物理外键。';

COMMENT ON COLUMN card_secret_inventory.id IS '卡密库存主键，由 HybridSemaphoreIdWorker 生成的 16 字节 Hybrid ID；接口和视图使用该字节值转出的 Base62 字符串';
COMMENT ON COLUMN card_secret_inventory.sku_id IS '所属商品 SKU ID，对应 product_sku.id；本字段为 16 字节 Hybrid ID 逻辑外键，不创建物理外键；接口和 URL 使用该字节值转出的 Base62 字符串';
COMMENT ON COLUMN card_secret_inventory.batch_no IS '导入批次号，用于追踪某一次卡密文本文件导入';
COMMENT ON COLUMN card_secret_inventory.secret_ciphertext IS '加密后的卡密密文，建议保存 AES-256-GCM 输出的 Base64 文本，包含认证标签；需要发货展示时由应用使用对应版本密钥解密';
COMMENT ON COLUMN card_secret_inventory.secret_nonce IS '加密使用的 nonce/iv，建议保存 12 字节随机 nonce 的 Base64 文本；它不是密钥，可以和密文一起存储';
COMMENT ON COLUMN card_secret_inventory.secret_hash IS '卡密明文的 HMAC-SHA256 摘要，用于重复导入检测；不可逆，不能用于还原卡密明文';
COMMENT ON COLUMN card_secret_inventory.secret_key_version IS '加密和摘要使用的密钥版本，只记录版本号，不保存真实密钥；真实密钥由应用配置或密钥管理服务提供';
COMMENT ON COLUMN card_secret_inventory.encrypt_algorithm IS '加密算法标识，当前只允许 AES_256_GCM';
COMMENT ON COLUMN card_secret_inventory.hash_algorithm IS '摘要算法标识，当前只允许 HMAC_SHA256';
COMMENT ON COLUMN card_secret_inventory.status IS '卡密状态：UNUSED 未发放，SOLD 已发放，DISABLED 已禁用';
COMMENT ON COLUMN card_secret_inventory.order_no IS '发放后关联的订单号，对应 trade_order.order_no；本字段为逻辑外键，不创建物理外键';
COMMENT ON COLUMN card_secret_inventory.user_id IS '发放后关联的用户 ID，对应 user_profile.id；本字段为逻辑外键，不创建物理外键';
COMMENT ON COLUMN card_secret_inventory.sold_at IS '卡密发放时间';
COMMENT ON COLUMN card_secret_inventory.disabled_at IS '卡密禁用时间';
COMMENT ON COLUMN card_secret_inventory.remark IS '管理员备注或导入备注';
COMMENT ON COLUMN card_secret_inventory.created_at IS '创建时间';
COMMENT ON COLUMN card_secret_inventory.updated_at IS '更新时间';
COMMENT ON COLUMN card_secret_inventory.version IS '数据版本号，用于并发发放、禁用或后台修改时做乐观锁控制';
