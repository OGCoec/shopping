package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductSpuMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM product_spu p
            LEFT JOIN product_category c ON c.id = p.category_id
            <where>
                <if test="name != null and name != ''">
                    AND p.name ILIKE CONCAT('%', #{name}, '%')
                </if>
                <if test="categoryId != null">
                    AND p.category_id = #{categoryId}
                </if>
                <if test="status != null and status != ''">
                    AND p.status = #{status}
                </if>
            </where>
            </script>
            """)
    long countSpuPage(@Param("name") String name,
                      @Param("categoryId") Long categoryId,
                      @Param("status") String status);

    @Select("""
            <script>
            SELECT p.id AS "id",
                   p.category_id AS "categoryId",
                   c.name AS "categoryName",
                   p.name AS "name",
                   p.subtitle AS "subtitle",
                   p.brand_name AS "brandName",
                   p.main_image_url AS "mainImageUrl",
                   p.status AS "status",
                   p.created_at AS "createdAt",
                   p.updated_at AS "updatedAt"
            FROM product_spu p
            LEFT JOIN product_category c ON c.id = p.category_id
            <where>
                <if test="name != null and name != ''">
                    AND p.name ILIKE CONCAT('%', #{name}, '%')
                </if>
                <if test="categoryId != null">
                    AND p.category_id = #{categoryId}
                </if>
                <if test="status != null and status != ''">
                    AND p.status = #{status}
                </if>
            </where>
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listSpuPage(@Param("name") String name,
                                          @Param("categoryId") Long categoryId,
                                          @Param("status") String status,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);

    @Select("""
            SELECT p.id AS "id",
                   p.category_id AS "categoryId",
                   c.name AS "categoryName",
                   p.name AS "name",
                   p.subtitle AS "subtitle",
                   p.brand_name AS "brandName",
                   p.main_image_url AS "mainImageUrl",
                   p.status AS "status",
                   p.created_at AS "createdAt",
                   p.updated_at AS "updatedAt"
            FROM product_spu p
            LEFT JOIN product_category c ON c.id = p.category_id
            WHERE p.id = #{id}
            """)
    Map<String, Object> findSpuById(@Param("id") Long id);

    @Select("""
            SELECT p.id AS "id",
                   p.category_id AS "categoryId",
                   c.name AS "categoryName",
                   p.name AS "name",
                   p.subtitle AS "subtitle",
                   p.brand_name AS "brandName",
                   p.main_image_url AS "mainImageUrl",
                   p.status AS "status",
                   p.created_at AS "createdAt",
                   p.updated_at AS "updatedAt",
                   COALESCE(d.image_urls, '[]'::jsonb)::text AS "imageUrlsJson",
                   COALESCE(d.detail_image_urls, '[]'::jsonb)::text AS "detailImageUrlsJson",
                   COALESCE(d.attributes, '{}'::jsonb)::text AS "attributesJson",
                   d.description AS "description",
                   d.after_sale AS "afterSale",
                   COALESCE((
                       SELECT jsonb_agg(
                                  jsonb_build_object(
                                      'id', s.id,
                                      'spuId', s.spu_id,
                                      'skuCode', s.sku_code,
                                      'skuName', s.sku_name,
                                      'specJson', s.spec_json,
                                      'skuImageUrl', s.sku_image_url,
                                      'priceYuan', s.price_yuan,
                                      'originalPriceYuan', s.original_price_yuan,
                                      'stockQuantity', s.stock_quantity,
                                      'status', s.status
                                  )
                                  ORDER BY s.created_at ASC, s.id ASC
                              )
                       FROM product_sku s
                       WHERE s.spu_id = p.id
                   ), '[]'::jsonb)::text AS "skusJson"
            FROM product_spu p
            LEFT JOIN product_category c ON c.id = p.category_id
            LEFT JOIN product_detail d ON d.spu_id = p.id
            WHERE p.id = #{id}
            """)
    Map<String, Object> findSpuDetailById(@Param("id") Long id);

    @Select("""
            WITH target_spu AS (
                SELECT p.id, p.main_image_url
                FROM product_spu p
                WHERE p.id = #{id}
                FOR UPDATE
            ),
            target_category AS (
                SELECT c.id,
                       c.status,
                       (SELECT COUNT(*) FROM product_category child WHERE child.parent_id = c.id) AS child_count
                FROM product_category c
                WHERE c.id = #{categoryId}
            ),
            incoming_sku AS (
                SELECT NULLIF(raw.id, '') AS requested_id,
                       NULLIF(raw.generated_id, '') AS generated_id,
                       raw.sku_code,
                       raw.sku_name,
                       COALESCE(raw.spec_json, '{}'::jsonb) AS spec_json,
                       NULLIF(raw.sku_image_url, '') AS sku_image_url,
                       raw.price_yuan,
                       raw.original_price_yuan,
                       raw.stock_quantity,
                       raw.status
                FROM jsonb_to_recordset(CAST(#{skusJson} AS jsonb)) AS raw(
                    id text,
                    generated_id text,
                    sku_code text,
                    sku_name text,
                    spec_json jsonb,
                    sku_image_url text,
                    price_yuan numeric,
                    original_price_yuan numeric,
                    stock_quantity integer,
                    status text
                )
            ),
            normalized_sku AS (
                SELECT COALESCE(requested_id, generated_id) AS id,
                       requested_id,
                       sku_code,
                       sku_name,
                       spec_json,
                       sku_image_url,
                       price_yuan,
                       original_price_yuan,
                       stock_quantity,
                       status
                FROM incoming_sku
            ),
            invalid_sku AS (
                SELECT COUNT(*) AS count
                FROM normalized_sku n
                WHERE n.id IS NULL
                   OR n.id !~ '^[0-9a-f]{32}$'
                   OR (
                       n.requested_id IS NOT NULL
                       AND NOT EXISTS (
                           SELECT 1
                           FROM product_sku s
                           WHERE s.id = n.requested_id
                             AND s.spu_id = #{id}
                       )
                   )
            ),
            old_sku AS (
                SELECT s.id, s.sku_image_url
                FROM product_sku s
                INNER JOIN target_spu p ON p.id = s.spu_id
            ),
            old_detail AS (
                SELECT d.image_urls, d.detail_image_urls
                FROM product_detail d
                INNER JOIN target_spu p ON p.id = d.spu_id
            ),
            old_raw_image_urls AS (
                SELECT main_image_url AS url
                FROM target_spu
                UNION ALL
                SELECT sku_image_url AS url
                FROM old_sku
                UNION ALL
                SELECT CASE
                           WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                           WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                           ELSE NULL
                       END AS url
                FROM old_detail d
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE WHEN jsonb_typeof(d.image_urls) = 'array' THEN d.image_urls ELSE '[]'::jsonb END
                ) AS item(value)
                UNION ALL
                SELECT CASE
                           WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                           WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                           ELSE NULL
                       END AS url
                FROM old_detail d
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE WHEN jsonb_typeof(d.detail_image_urls) = 'array' THEN d.detail_image_urls ELSE '[]'::jsonb END
                ) AS item(value)
            ),
            old_image_urls AS (
                SELECT DISTINCT url
                FROM old_raw_image_urls
                WHERE url IS NOT NULL
                  AND BTRIM(url) != ''
            ),
            updated_spu AS (
                UPDATE product_spu p
                SET category_id = #{categoryId},
                    subtitle = #{subtitle},
                    brand_name = #{brandName},
                    main_image_url = #{mainImageUrl},
                    status = #{status},
                    updated_at = NOW()
                WHERE p.id = #{id}
                  AND EXISTS (SELECT 1 FROM target_spu)
                  AND EXISTS (
                      SELECT 1
                      FROM target_category c
                      WHERE c.child_count = 0
                        AND c.status = 'ACTIVE'
                  )
                  AND (SELECT count FROM invalid_sku) = 0
                RETURNING p.id
            ),
            upsert_detail AS (
                INSERT INTO product_detail (
                    id,
                    spu_id,
                    image_urls,
                    detail_image_urls,
                    attributes,
                    description,
                    after_sale
                )
                SELECT #{detailId},
                       #{id},
                       CAST(#{imageUrlsJson} AS jsonb),
                       CAST(#{detailImageUrlsJson} AS jsonb),
                       CAST(#{attributesJson} AS jsonb),
                       #{description},
                       #{afterSale}
                WHERE EXISTS (SELECT 1 FROM updated_spu)
                ON CONFLICT (spu_id) DO UPDATE
                SET image_urls = EXCLUDED.image_urls,
                    detail_image_urls = EXCLUDED.detail_image_urls,
                    attributes = EXCLUDED.attributes,
                    description = EXCLUDED.description,
                    after_sale = EXCLUDED.after_sale,
                    updated_at = NOW()
                RETURNING id
            ),
            deleted_sku AS (
                DELETE FROM product_sku s
                USING target_spu p
                WHERE s.spu_id = p.id
                  AND EXISTS (SELECT 1 FROM updated_spu)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM normalized_sku n
                      WHERE n.id = s.id
                  )
                RETURNING s.id
            ),
            upserted_sku AS (
                INSERT INTO product_sku (
                    id,
                    spu_id,
                    sku_code,
                    sku_name,
                    spec_json,
                    sku_image_url,
                    price_yuan,
                    original_price_yuan,
                    stock_quantity,
                    status
                )
                SELECT n.id,
                       #{id},
                       n.sku_code,
                       n.sku_name,
                       n.spec_json,
                       n.sku_image_url,
                       n.price_yuan,
                       n.original_price_yuan,
                       n.stock_quantity,
                       n.status
                FROM normalized_sku n
                WHERE EXISTS (SELECT 1 FROM updated_spu)
                ON CONFLICT (id) DO UPDATE
                SET sku_code = EXCLUDED.sku_code,
                    sku_name = EXCLUDED.sku_name,
                    spec_json = EXCLUDED.spec_json,
                    sku_image_url = EXCLUDED.sku_image_url,
                    price_yuan = EXCLUDED.price_yuan,
                    original_price_yuan = EXCLUDED.original_price_yuan,
                    stock_quantity = EXCLUDED.stock_quantity,
                    status = EXCLUDED.status,
                    updated_at = NOW()
                RETURNING id
            )
            SELECT EXISTS(SELECT 1 FROM target_spu) AS "spuExists",
                   EXISTS(SELECT 1 FROM target_category) AS "categoryExists",
                   COALESCE((SELECT child_count FROM target_category), 0) AS "childCount",
                   COALESCE((SELECT status FROM target_category), '') AS "categoryStatus",
                   (SELECT count FROM invalid_sku) AS "invalidSkuCount",
                   (SELECT COUNT(*) FROM updated_spu) AS "updatedSpuCount",
                   (SELECT COUNT(*) FROM upsert_detail) AS "updatedDetailCount",
                   (SELECT COUNT(*) FROM deleted_sku) AS "deletedSkuCount",
                   (SELECT COUNT(*) FROM upserted_sku) AS "upsertedSkuCount",
                   COALESCE((SELECT jsonb_agg(url) FROM old_image_urls), '[]'::jsonb)::text AS "oldImageUrlsJson"
            """)
    Map<String, Object> updateSpuDetail(@Param("id") Long id,
                                        @Param("categoryId") Long categoryId,
                                        @Param("subtitle") String subtitle,
                                        @Param("brandName") String brandName,
                                        @Param("mainImageUrl") String mainImageUrl,
                                        @Param("status") String status,
                                        @Param("detailId") Long detailId,
                                        @Param("imageUrlsJson") String imageUrlsJson,
                                        @Param("detailImageUrlsJson") String detailImageUrlsJson,
                                        @Param("attributesJson") String attributesJson,
                                        @Param("description") String description,
                                        @Param("afterSale") String afterSale,
                                        @Param("skusJson") String skusJson);

    @Select("""
            SELECT id
            FROM product_spu
            ORDER BY id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Long> listAllSpuIds(@Param("limit") int limit, @Param("offset") long offset);

    @Insert("""
            INSERT INTO product_spu (
                id,
                category_id,
                name,
                subtitle,
                brand_name,
                main_image_url,
                status
            ) VALUES (
                #{id},
                #{categoryId},
                #{name},
                #{subtitle},
                #{brandName},
                #{mainImageUrl},
                #{status}
            )
            """)
    int insertSpu(@Param("id") Long id,
                  @Param("categoryId") Long categoryId,
                  @Param("name") String name,
                  @Param("subtitle") String subtitle,
                  @Param("brandName") String brandName,
                  @Param("mainImageUrl") String mainImageUrl,
                  @Param("status") String status);

    @Update("""
            UPDATE product_spu
            SET status = #{status},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("""
            <script>
            WITH requested(id) AS (
                VALUES
                <foreach collection="ids" item="id" separator=",">
                    (#{id})
                </foreach>
            ),
            normalized_requested AS (
                SELECT DISTINCT id
                FROM requested
            ),
            target_spu AS (
                SELECT p.id
                FROM product_spu p
                INNER JOIN normalized_requested r ON r.id = p.id
            ),
            updated AS (
                UPDATE product_spu p
                SET status = #{status},
                    updated_at = NOW()
                FROM target_spu t
                WHERE p.id = t.id
                  AND p.status IS DISTINCT FROM #{status}
                  AND (SELECT COUNT(*) FROM target_spu) = (SELECT COUNT(*) FROM normalized_requested)
                RETURNING p.id
            )
            SELECT (SELECT COUNT(*) FROM normalized_requested) AS "requestedCount",
                   (SELECT COUNT(*) FROM target_spu) AS "matchedCount",
                   (SELECT COUNT(*) FROM updated) AS "affectedCount",
                   COALESCE((SELECT jsonb_agg(id) FROM target_spu), '[]'::jsonb)::text AS "targetIdsJson"
            </script>
            """)
    Map<String, Object> batchUpdateStatusByIds(@Param("ids") List<Long> ids,
                                               @Param("status") String status);

    @Select("""
            WITH target_category AS (
                SELECT c.id,
                       (SELECT COUNT(*) FROM product_category child WHERE child.parent_id = c.id) AS child_count
                FROM product_category c
                WHERE c.id = #{categoryId}
            ),
            target_spu AS (
                SELECT p.id
                FROM product_spu p
                INNER JOIN target_category c ON c.id = p.category_id
                WHERE c.child_count = 0
            ),
            updated AS (
                UPDATE product_spu p
                SET status = #{status},
                    updated_at = NOW()
                FROM target_spu t
                WHERE p.id = t.id
                  AND p.status IS DISTINCT FROM #{status}
                RETURNING p.id
            )
            SELECT EXISTS(SELECT 1 FROM target_category) AS "categoryExists",
                   COALESCE((SELECT child_count FROM target_category), 0) AS "childCount",
                   (SELECT COUNT(*) FROM target_spu) AS "requestedCount",
                   (SELECT COUNT(*) FROM target_spu) AS "matchedCount",
                   (SELECT COUNT(*) FROM updated) AS "affectedCount",
                   COALESCE((SELECT jsonb_agg(id) FROM target_spu), '[]'::jsonb)::text AS "targetIdsJson"
            """)
    Map<String, Object> batchUpdateStatusByLeafCategory(@Param("categoryId") Long categoryId,
                                                        @Param("status") String status);

    @Select("""
            <script>
            WITH requested(id) AS (
                VALUES
                <foreach collection="ids" item="id" separator=",">
                    (#{id})
                </foreach>
            ),
            normalized_requested AS (
                SELECT DISTINCT id
                FROM requested
            ),
            target_spu AS (
                SELECT p.id, p.main_image_url
                FROM product_spu p
                INNER JOIN normalized_requested r ON r.id = p.id
            ),
            delete_allowed AS (
                SELECT (SELECT COUNT(*) FROM target_spu) = (SELECT COUNT(*) FROM normalized_requested) AS allowed
            ),
            target_sku AS (
                SELECT s.id, s.sku_image_url
                FROM product_sku s
                INNER JOIN target_spu p ON p.id = s.spu_id
            ),
            target_detail AS (
                SELECT d.id, d.image_urls, d.detail_image_urls
                FROM product_detail d
                INNER JOIN target_spu p ON p.id = d.spu_id
            ),
            raw_image_urls AS (
                SELECT main_image_url AS url
                FROM target_spu
                UNION ALL
                SELECT sku_image_url AS url
                FROM target_sku
                UNION ALL
                SELECT CASE
                           WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                           WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                           ELSE NULL
                       END AS url
                FROM target_detail d
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE WHEN jsonb_typeof(d.image_urls) = 'array' THEN d.image_urls ELSE '[]'::jsonb END
                ) AS item(value)
                UNION ALL
                SELECT CASE
                           WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                           WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                           ELSE NULL
                       END AS url
                FROM target_detail d
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE WHEN jsonb_typeof(d.detail_image_urls) = 'array' THEN d.detail_image_urls ELSE '[]'::jsonb END
                ) AS item(value)
            ),
            image_urls AS (
                SELECT DISTINCT url
                FROM raw_image_urls
                WHERE url IS NOT NULL
                  AND BTRIM(url) != ''
            ),
            deleted_detail AS (
                DELETE FROM product_detail d
                USING target_detail t, delete_allowed allow
                WHERE d.id = t.id
                  AND allow.allowed
                RETURNING d.id
            ),
            deleted_sku AS (
                DELETE FROM product_sku s
                USING target_sku t, delete_allowed allow
                WHERE s.id = t.id
                  AND allow.allowed
                RETURNING s.id
            ),
            deleted_spu AS (
                DELETE FROM product_spu p
                USING target_spu t, delete_allowed allow
                WHERE p.id = t.id
                  AND allow.allowed
                RETURNING p.id
            )
            SELECT (SELECT COUNT(*) FROM normalized_requested) AS "requestedCount",
                   (SELECT COUNT(*) FROM target_spu) AS "matchedCount",
                   (SELECT COUNT(*) FROM deleted_spu) AS "deletedSpuCount",
                   (SELECT COUNT(*) FROM deleted_sku) AS "deletedSkuCount",
                   (SELECT COUNT(*) FROM deleted_detail) AS "deletedDetailCount",
                   COALESCE((SELECT jsonb_agg(id) FROM deleted_spu), '[]'::jsonb)::text AS "deletedIdsJson",
                   COALESCE((SELECT jsonb_agg(url) FROM image_urls), '[]'::jsonb)::text AS "imageUrlsJson"
            </script>
            """)
    Map<String, Object> batchDeleteByIds(@Param("ids") List<Long> ids);

    @Select("""
            WITH target_category AS (
                SELECT c.id,
                       (SELECT COUNT(*) FROM product_category child WHERE child.parent_id = c.id) AS child_count
                FROM product_category c
                WHERE c.id = #{categoryId}
            ),
            target_spu AS (
                SELECT p.id, p.main_image_url
                FROM product_spu p
                INNER JOIN target_category c ON c.id = p.category_id
                WHERE c.child_count = 0
            ),
            target_sku AS (
                SELECT s.id, s.sku_image_url
                FROM product_sku s
                INNER JOIN target_spu p ON p.id = s.spu_id
            ),
            target_detail AS (
                SELECT d.id, d.image_urls, d.detail_image_urls
                FROM product_detail d
                INNER JOIN target_spu p ON p.id = d.spu_id
            ),
            raw_image_urls AS (
                SELECT main_image_url AS url
                FROM target_spu
                UNION ALL
                SELECT sku_image_url AS url
                FROM target_sku
                UNION ALL
                SELECT CASE
                           WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                           WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                           ELSE NULL
                       END AS url
                FROM target_detail d
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE WHEN jsonb_typeof(d.image_urls) = 'array' THEN d.image_urls ELSE '[]'::jsonb END
                ) AS item(value)
                UNION ALL
                SELECT CASE
                           WHEN jsonb_typeof(item.value) = 'string' THEN item.value #>> '{}'
                           WHEN jsonb_typeof(item.value) = 'object' THEN item.value ->> 'url'
                           ELSE NULL
                       END AS url
                FROM target_detail d
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE WHEN jsonb_typeof(d.detail_image_urls) = 'array' THEN d.detail_image_urls ELSE '[]'::jsonb END
                ) AS item(value)
            ),
            image_urls AS (
                SELECT DISTINCT url
                FROM raw_image_urls
                WHERE url IS NOT NULL
                  AND BTRIM(url) != ''
            ),
            deleted_detail AS (
                DELETE FROM product_detail d
                USING target_detail t
                WHERE d.id = t.id
                RETURNING d.id
            ),
            deleted_sku AS (
                DELETE FROM product_sku s
                USING target_sku t
                WHERE s.id = t.id
                RETURNING s.id
            ),
            deleted_spu AS (
                DELETE FROM product_spu p
                USING target_spu t
                WHERE p.id = t.id
                RETURNING p.id
            )
            SELECT EXISTS(SELECT 1 FROM target_category) AS "categoryExists",
                   COALESCE((SELECT child_count FROM target_category), 0) AS "childCount",
                   (SELECT COUNT(*) FROM target_spu) AS "requestedCount",
                   (SELECT COUNT(*) FROM target_spu) AS "matchedCount",
                   (SELECT COUNT(*) FROM deleted_spu) AS "deletedSpuCount",
                   (SELECT COUNT(*) FROM deleted_sku) AS "deletedSkuCount",
                   (SELECT COUNT(*) FROM deleted_detail) AS "deletedDetailCount",
                   COALESCE((SELECT jsonb_agg(id) FROM deleted_spu), '[]'::jsonb)::text AS "deletedIdsJson",
                   COALESCE((SELECT jsonb_agg(url) FROM image_urls), '[]'::jsonb)::text AS "imageUrlsJson"
            """)
    Map<String, Object> batchDeleteByLeafCategory(@Param("categoryId") Long categoryId);
}
