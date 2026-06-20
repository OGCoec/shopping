package com.example.ShoppingSystem.mapper.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductCategoryMapper {


    List<Map<String, Object>> listActivePublicCategoryRows();


    List<Long> listActiveCategoryIds(@Param("limit") int limit, @Param("offset") long offset);


    List<Map<String, Object>> listCategoryTreeRows();


    List<Map<String, Object>> listCategorySearchDisplayRows(@Param("ids") List<Long> ids);


    List<Map<String, Object>> listActiveCategorySearchDisplayRows(@Param("ids") List<Long> ids);


    List<Map<String, Object>> listCategoryIndexDocuments(@Param("limit") int limit,
                                                         @Param("offset") long offset);


    List<Map<String, Object>> listCategoryIndexDocumentsByIds(@Param("ids") List<Long> ids);

    long countExistingCategoryIds(@Param("ids") List<Long> ids);


    Map<String, Object> findCategoryTreeRowById(@Param("id") Long id);


    Map<String, Object> findCategoryById(@Param("id") Long id);


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


    int updateCategoryContent(@Param("id") Long id,
                              @Param("name") String name,
                              @Param("code") String code,
                              @Param("sortOrder") int sortOrder,
                              @Param("iconUrlsJson") String iconUrlsJson,
                              @Param("description") String description);


    int markParentAsNonLeaf(@Param("parentId") Long parentId);


    Map<String, Object> disableSubtreesIfAllowed(@Param("ids") List<Long> ids);


    int enableCategory(@Param("id") Long id);


    Map<String, Object> deleteCategoryIfAllowed(@Param("id") Long id);
}
