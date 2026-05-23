package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductCategoryMapper {

    @Select("""
            SELECT id AS "id",
                   parent_id AS "parentId",
                   name AS "name",
                   code AS "code",
                   level AS "level",
                   sort_order AS "sortOrder",
                   COALESCE(icon_urls, '[]'::jsonb)::text AS "iconUrlsJson",
                   status AS "status"
            FROM product_category
            WHERE status = 'ACTIVE'
            ORDER BY level ASC, parent_id ASC, sort_order ASC, id ASC
            """)
    List<Map<String, Object>> listActivePublicCategoryRows();

    @Select("""
            WITH child_counts AS (
                SELECT parent_id, COUNT(*) AS child_count
                FROM product_category
                GROUP BY parent_id
            ),
            product_counts AS (
                SELECT category_id,
                       COUNT(*) AS product_count,
                       COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_product_count
                FROM product_spu
                GROUP BY category_id
            )
            SELECT c.id AS "id",
                   c.parent_id AS "parentId",
                   c.name AS "name",
                   c.code AS "code",
                   c.level AS "level",
                   c.path AS "path",
                   c.sort_order AS "sortOrder",
                   COALESCE(c.icon_urls, '[]'::jsonb)::text AS "iconUrlsJson",
                   c.description AS "description",
                   c.status AS "status",
                   c.is_leaf AS "isLeaf",
                   COALESCE(cc.child_count, 0) AS "childCount",
                   COALESCE(pc.product_count, 0) AS "productCount",
                   COALESCE(pc.active_product_count, 0) AS "activeProductCount",
                   c.created_at AS "createdAt",
                   c.updated_at AS "updatedAt"
            FROM product_category c
            LEFT JOIN child_counts cc ON cc.parent_id = c.id
            LEFT JOIN product_counts pc ON pc.category_id = c.id
            ORDER BY c.level ASC, c.parent_id ASC, c.sort_order ASC, c.id ASC
            """)
    List<Map<String, Object>> listCategoryTreeRows();

    @Select("""
            SELECT c.id AS "id",
                   c.parent_id AS "parentId",
                   c.name AS "name",
                   c.code AS "code",
                   c.level AS "level",
                   c.path AS "path",
                   c.sort_order AS "sortOrder",
                   COALESCE(c.icon_urls, '[]'::jsonb)::text AS "iconUrlsJson",
                   c.description AS "description",
                   c.status AS "status",
                   c.is_leaf AS "isLeaf",
                   COALESCE((SELECT COUNT(*) FROM product_category child WHERE child.parent_id = c.id), 0) AS "childCount",
                   COALESCE((SELECT COUNT(*) FROM product_spu p WHERE p.category_id = c.id), 0) AS "productCount",
                   COALESCE((SELECT COUNT(*) FROM product_spu p WHERE p.category_id = c.id AND p.status = 'ACTIVE'), 0) AS "activeProductCount",
                   c.created_at AS "createdAt",
                   c.updated_at AS "updatedAt"
            FROM product_category c
            WHERE c.id = #{id}
            """)
    Map<String, Object> findCategoryTreeRowById(@Param("id") Long id);

    @Select("""
            SELECT id AS "id",
                   parent_id AS "parentId",
                   name AS "name",
                   code AS "code",
                   level AS "level",
                   path AS "path",
                   sort_order AS "sortOrder",
                   COALESCE(icon_urls, '[]'::jsonb)::text AS "iconUrlsJson",
                   description AS "description",
                   status AS "status",
                   is_leaf AS "isLeaf",
                   COALESCE((SELECT COUNT(*) FROM product_category child WHERE child.parent_id = product_category.id), 0) AS "childCount",
                   COALESCE((SELECT COUNT(*) FROM product_spu p WHERE p.category_id = product_category.id), 0) AS "productCount",
                   COALESCE((SELECT COUNT(*) FROM product_spu p WHERE p.category_id = product_category.id AND p.status = 'ACTIVE'), 0) AS "activeProductCount"
            FROM product_category
            WHERE id = #{id}
            """)
    Map<String, Object> findCategoryById(@Param("id") Long id);

    @Insert("""
            INSERT INTO product_category (
                id,
                parent_id,
                name,
                code,
                level,
                path,
                sort_order,
                icon_urls,
                description,
                status,
                is_leaf
            ) VALUES (
                #{id},
                #{parentId},
                #{name},
                #{code},
                #{level},
                #{path},
                #{sortOrder},
                CAST(#{iconUrlsJson} AS jsonb),
                #{description},
                #{status},
                TRUE
            )
            """)
    int insertCategory(@Param("id") Long id,
                       @Param("parentId") Long parentId,
                       @Param("name") String name,
                       @Param("code") String code,
                       @Param("level") int level,
                       @Param("path") String path,
                       @Param("sortOrder") int sortOrder,
                       @Param("iconUrlsJson") String iconUrlsJson,
                       @Param("description") String description,
                       @Param("status") String status);

    @Update("""
            UPDATE product_category
            SET name = #{name},
                code = #{code},
                sort_order = #{sortOrder},
                icon_urls = CAST(#{iconUrlsJson} AS jsonb),
                description = #{description},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int updateCategoryContent(@Param("id") Long id,
                              @Param("name") String name,
                              @Param("code") String code,
                              @Param("sortOrder") int sortOrder,
                              @Param("iconUrlsJson") String iconUrlsJson,
                              @Param("description") String description);

    @Update("""
            UPDATE product_category
            SET is_leaf = FALSE,
                updated_at = NOW()
            WHERE id = #{parentId}
            """)
    int markParentAsNonLeaf(@Param("parentId") Long parentId);

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
            target_roots AS (
                SELECT c.id, c.path
                FROM product_category c
                INNER JOIN normalized_requested r ON r.id = c.id
            ),
            target_tree AS (
                SELECT DISTINCT c.id
                FROM product_category c
                INNER JOIN target_roots root ON c.path LIKE root.path || '%'
            ),
            active_products AS (
                SELECT COUNT(*) AS active_product_count
                FROM product_spu p
                INNER JOIN target_tree t ON t.id = p.category_id
                WHERE p.status = 'ACTIVE'
            ),
            updated AS (
                UPDATE product_category c
                SET status = 'DISABLED',
                    updated_at = NOW()
                FROM target_tree t
                WHERE c.id = t.id
                  AND c.status IS DISTINCT FROM 'DISABLED'
                  AND (SELECT COUNT(*) FROM target_roots) = (SELECT COUNT(*) FROM normalized_requested)
                  AND (SELECT active_product_count FROM active_products) = 0
                RETURNING c.id
            )
            SELECT (SELECT COUNT(*) FROM normalized_requested) AS "requestedCount",
                   (SELECT COUNT(*) FROM target_roots) AS "rootCount",
                   (SELECT COUNT(*) FROM target_tree) AS "subtreeCount",
                   (SELECT active_product_count FROM active_products) AS "activeProductCount",
                   (SELECT COUNT(*) FROM updated) AS "affectedCount"
            </script>
            """)
    Map<String, Object> disableSubtreesIfAllowed(@Param("ids") List<Long> ids);

    @Update("""
            UPDATE product_category
            SET status = 'ACTIVE',
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int enableCategory(@Param("id") Long id);

    @Select("""
            WITH target AS (
                SELECT id, parent_id
                FROM product_category
                WHERE id = #{id}
            ),
            stats AS (
                SELECT t.id,
                       t.parent_id,
                       (SELECT COUNT(*) FROM product_category child WHERE child.parent_id = t.id) AS child_count,
                       (SELECT COUNT(*) FROM product_spu p WHERE p.category_id = t.id) AS product_count,
                       (SELECT COUNT(*) FROM product_spu p WHERE p.category_id = t.id AND p.status = 'ACTIVE') AS active_product_count
                FROM target t
            ),
            deleted AS (
                DELETE FROM product_category c
                USING stats s
                WHERE c.id = s.id
                  AND s.child_count = 0
                  AND s.active_product_count = 0
                RETURNING c.id, c.parent_id
            ),
            parent_update AS (
                UPDATE product_category parent
                SET is_leaf = NOT EXISTS (
                        SELECT 1
                        FROM product_category child
                        WHERE child.parent_id = parent.id
                    ),
                    updated_at = NOW()
                FROM deleted d
                WHERE parent.id = d.parent_id
                  AND d.parent_id > 0
                RETURNING parent.id
            )
            SELECT EXISTS(SELECT 1 FROM target) AS "categoryExists",
                   COALESCE((SELECT parent_id FROM stats), 0) AS "parentId",
                   COALESCE((SELECT child_count FROM stats), 0) AS "childCount",
                   COALESCE((SELECT product_count FROM stats), 0) AS "productCount",
                   COALESCE((SELECT active_product_count FROM stats), 0) AS "activeProductCount",
                   EXISTS(SELECT 1 FROM deleted) AS "deleted"
            """)
    Map<String, Object> deleteCategoryIfAllowed(@Param("id") Long id);
}
