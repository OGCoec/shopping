-- ============================================
-- 文件名：015_create_product_category.sql
-- 说明：商品分类表
-- 约定：
-- 1. 一行代表一个商品分类节点；
-- 2. parent_id 表示父分类 ID，顶级分类 parent_id 为 0；
-- 3. level 表示分类层级，path 表示从根分类到当前分类的完整路径；
-- 4. icon_urls 使用 JSONB 数组保存多个分类图标地址或不同场景的图标信息；
-- 5. 商品建议只挂载到 is_leaf = TRUE 的叶子分类。
-- ============================================

CREATE TABLE IF NOT EXISTS product_category (
    -- 分类 ID，由业务侧雪花 ID 生成
    id BIGINT PRIMARY KEY,

    -- 父分类 ID，顶级分类为 0
    parent_id BIGINT NOT NULL DEFAULT 0,

    -- 分类名称，用于前台展示
    name VARCHAR(64) NOT NULL,

    -- 分类编码，用于程序识别、路由或幂等导入
    code VARCHAR(64) NOT NULL,

    -- 分类层级，例如 1 表示一级分类，2 表示二级分类
    level SMALLINT NOT NULL DEFAULT 1,

    -- 分类路径，例如 /1001/1002/1003/
    path VARCHAR(512) NOT NULL,

    -- 同级分类排序值，值越小越靠前
    sort_order INTEGER NOT NULL DEFAULT 0,

    -- 分类图标集合，JSON 数组格式
    icon_urls JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 分类描述
    description VARCHAR(255),

    -- 分类状态：ACTIVE 启用，DISABLED 禁用
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',

    -- 是否叶子分类，叶子分类下面不再有子分类
    is_leaf BOOLEAN NOT NULL DEFAULT TRUE,

    -- 创建时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 更新时间
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_product_category_code UNIQUE (code),

    CONSTRAINT uq_product_category_parent_name UNIQUE (parent_id, name),

    CONSTRAINT ck_product_category_parent_id
        CHECK (parent_id >= 0),

    CONSTRAINT ck_product_category_not_self_parent
        CHECK (id <> parent_id),

    CONSTRAINT ck_product_category_level
        CHECK (level >= 1),

    CONSTRAINT ck_product_category_path
        CHECK (path LIKE '/%/'),

    CONSTRAINT ck_product_category_icon_urls_array
        CHECK (jsonb_typeof(icon_urls) = 'array'),

    CONSTRAINT ck_product_category_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_product_category_parent_id
    ON product_category (parent_id);

CREATE INDEX IF NOT EXISTS idx_product_category_status_sort
    ON product_category (status, sort_order);

CREATE INDEX IF NOT EXISTS idx_product_category_path_prefix
    ON product_category (path varchar_pattern_ops);

COMMENT ON TABLE product_category IS '商品分类表，用于维护商品分类树';

COMMENT ON COLUMN product_category.id IS '分类 ID，由业务侧雪花 ID 生成';
COMMENT ON COLUMN product_category.parent_id IS '父分类 ID，顶级分类为 0';
COMMENT ON COLUMN product_category.name IS '分类名称，用于前台展示';
COMMENT ON COLUMN product_category.code IS '分类编码，用于程序识别、路由或幂等导入';
COMMENT ON COLUMN product_category.level IS '分类层级，例如 1 表示一级分类，2 表示二级分类';
COMMENT ON COLUMN product_category.path IS '分类路径，例如 /1001/1002/1003/，用于查询整棵子分类树';
COMMENT ON COLUMN product_category.sort_order IS '同级分类排序值，值越小越靠前';
COMMENT ON COLUMN product_category.icon_urls IS '分类图标集合，JSON 数组格式，例如 [{"type":"default","url":"https://example.com/category.png"}]';
COMMENT ON COLUMN product_category.description IS '分类描述';
COMMENT ON COLUMN product_category.status IS '分类状态：ACTIVE 启用，DISABLED 禁用';
COMMENT ON COLUMN product_category.is_leaf IS '是否叶子分类，叶子分类下面不再有子分类，商品通常只挂载到叶子分类';
COMMENT ON COLUMN product_category.created_at IS '创建时间';
COMMENT ON COLUMN product_category.updated_at IS '更新时间';
