-- 文件名：021_create_product_hot_sku.sql
-- 说明：创建热点 SKU 配置表，Redis 运行态库存由后台预热和手动清理维护

CREATE TABLE IF NOT EXISTS product_hot_sku (
    -- 热点配置 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID
    id BYTEA PRIMARY KEY,

    -- 所属商品 SPU ID
    spu_id BIGINT NOT NULL,

    -- 参与热点的 SKU ID，对应 product_sku.id
    sku_id BYTEA NOT NULL,

    -- 热点活动配置的总库存
    stock_quantity INTEGER NOT NULL,

    -- 热点活动当前剩余库存；用户端扣减上线后由 Redis 异步同步
    remaining_quantity INTEGER NOT NULL,

    -- 热点状态：ENABLED 启用，DISABLED 禁用，SOLD_OUT 售罄
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',

    -- 活动开始时间，仅作为业务判断元数据，Redis key 不依赖 TTL 自动过期
    start_at TIMESTAMPTZ NULL,

    -- 活动结束时间，仅作为业务判断元数据，Redis key 不依赖 TTL 自动过期
    end_at TIMESTAMPTZ NULL,

    -- 配置版本，每次覆盖热点配置递增
    version BIGINT NOT NULL DEFAULT 1,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_product_hot_sku_spu
        FOREIGN KEY (spu_id) REFERENCES product_spu(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_product_hot_sku_sku
        FOREIGN KEY (sku_id) REFERENCES product_sku(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_product_hot_sku_sku
        UNIQUE (sku_id),

    CONSTRAINT ck_product_hot_sku_id_bytes
        CHECK (octet_length(id) = 16),

    CONSTRAINT ck_product_hot_sku_sku_id_bytes
        CHECK (octet_length(sku_id) = 16),

    CONSTRAINT ck_product_hot_sku_stock_quantity
        CHECK (stock_quantity > 0),

    CONSTRAINT ck_product_hot_sku_remaining_quantity
        CHECK (remaining_quantity >= 0 AND remaining_quantity <= stock_quantity),

    CONSTRAINT ck_product_hot_sku_status
        CHECK (status IN ('ENABLED', 'DISABLED', 'SOLD_OUT')),

    CONSTRAINT ck_product_hot_sku_time_range
        CHECK (start_at IS NULL OR end_at IS NULL OR end_at > start_at)
);

CREATE INDEX IF NOT EXISTS idx_product_hot_sku_spu_id
    ON product_hot_sku (spu_id);

CREATE INDEX IF NOT EXISTS idx_product_hot_sku_spu_updated_created
    ON product_hot_sku (spu_id, updated_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_product_hot_sku_status
    ON product_hot_sku (status);

COMMENT ON TABLE product_hot_sku IS '热点 SKU 配置表，用于管理员预热热点库存到 Redis';
COMMENT ON COLUMN product_hot_sku.id IS '热点配置 ID，由 HybridSemaphoreIdWorker 生成的 16 字节 ID；接口返回 Base62 字符串';
COMMENT ON COLUMN product_hot_sku.spu_id IS '所属商品 SPU ID';
COMMENT ON COLUMN product_hot_sku.sku_id IS '热点 SKU ID，对应 product_sku.id';
COMMENT ON COLUMN product_hot_sku.stock_quantity IS '热点活动配置的总库存';
COMMENT ON COLUMN product_hot_sku.remaining_quantity IS '热点活动当前剩余库存';
COMMENT ON COLUMN product_hot_sku.status IS '热点状态：ENABLED 启用，DISABLED 禁用，SOLD_OUT 售罄';
COMMENT ON COLUMN product_hot_sku.start_at IS '热点活动开始时间，Redis 不按该字段设置 TTL';
COMMENT ON COLUMN product_hot_sku.end_at IS '热点活动结束时间，Redis 不按该字段设置 TTL';
COMMENT ON COLUMN product_hot_sku.version IS '热点配置版本，每次覆盖配置递增';
