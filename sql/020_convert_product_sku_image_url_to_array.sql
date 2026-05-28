-- ============================================
-- 文件名：020_convert_product_sku_image_url_to_array.sql
-- 说明：将 product_sku.sku_image_url 从单图 URL 调整为 JSON 字符串数组
-- 约定：
-- 1. 继续保留列名 sku_image_url；
-- 2. 空值转为 []；
-- 3. 旧单图 URL 转为 ["url"]；
-- 4. 已经是数组文本的值保持数组文本。
-- ============================================

ALTER TABLE product_sku
    DROP CONSTRAINT IF EXISTS ck_product_sku_image_urls_array;

ALTER TABLE product_sku
    ALTER COLUMN sku_image_url TYPE TEXT;

UPDATE product_sku
SET sku_image_url = CASE
    WHEN sku_image_url IS NULL OR BTRIM(sku_image_url) = '' THEN '[]'
    WHEN BTRIM(sku_image_url) LIKE '[%' THEN sku_image_url
    ELSE jsonb_build_array(sku_image_url)::text
END;

ALTER TABLE product_sku
    ALTER COLUMN sku_image_url SET DEFAULT '[]',
    ALTER COLUMN sku_image_url SET NOT NULL;

ALTER TABLE product_sku
    ADD CONSTRAINT ck_product_sku_image_urls_array
        CHECK (jsonb_typeof(sku_image_url::jsonb) = 'array');

COMMENT ON COLUMN product_sku.sku_image_url IS 'SKU 图片地址 JSON 字符串数组，可用于不同颜色或规格展示多张图片';
