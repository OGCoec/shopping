-- ============================================
-- 文件名称：019_normalize_product_image_urls_to_string_array.sql
-- 说明：将商品展示图和详情图 JSON 统一迁移为 URL 字符串数组
-- 约定：
-- 1. 字符串项原样保留；
-- 2. 对象项取 url 字段；
-- 3. 空 URL 会被过滤；
-- 4. image_urls 会按首次出现位置去重，detail_image_urls 保留原顺序。
-- ============================================

WITH normalized AS (
    SELECT d.id,
           COALESCE((
               SELECT jsonb_agg(url ORDER BY ord)
               FROM (
                   SELECT url, MIN(ord) AS ord
                   FROM (
                       SELECT item.ord,
                              BTRIM(CASE
                                  WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                                  WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                                  ELSE NULL
                              END) AS url
                       FROM jsonb_array_elements(
                           CASE WHEN jsonb_typeof(d.image_urls) = 'array' THEN d.image_urls ELSE '[]'::jsonb END
                       ) WITH ORDINALITY AS item(value, ord)
                   ) raw_display_urls
                   WHERE url IS NOT NULL
                     AND url != ''
                   GROUP BY url
               ) display_urls
           ), '[]'::jsonb) AS image_urls,
           COALESCE((
               SELECT jsonb_agg(url ORDER BY ord)
               FROM (
                   SELECT item.ord,
                          BTRIM(CASE
                              WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                              WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                              ELSE NULL
                          END) AS url
                   FROM jsonb_array_elements(
                       CASE WHEN jsonb_typeof(d.detail_image_urls) = 'array' THEN d.detail_image_urls ELSE '[]'::jsonb END
                   ) WITH ORDINALITY AS item(value, ord)
               ) detail_urls
               WHERE url IS NOT NULL
                 AND url != ''
           ), '[]'::jsonb) AS detail_image_urls
    FROM product_detail d
)
UPDATE product_detail d
SET image_urls = n.image_urls,
    detail_image_urls = n.detail_image_urls,
    updated_at = NOW()
FROM normalized n
WHERE d.id = n.id
  AND (
      d.image_urls IS DISTINCT FROM n.image_urls
      OR d.detail_image_urls IS DISTINCT FROM n.detail_image_urls
  );

COMMENT ON COLUMN product_detail.image_urls IS '商品展示图片 URL 字符串数组，数组顺序即展示顺序，例如 ["https://example.com/main.png","https://example.com/slide-1.png"]';
COMMENT ON COLUMN product_detail.detail_image_urls IS '商品详情图片 URL 字符串数组，数组顺序即展示顺序，例如 ["https://example.com/detail-1.png"]';
