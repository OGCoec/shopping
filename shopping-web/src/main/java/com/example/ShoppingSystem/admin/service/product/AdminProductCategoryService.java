package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryBatchDisableResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryStatusRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryTreeNodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminProductCategoryUpdateRequest;
import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.product.ProductCategoryMapper;
import com.example.ShoppingSystem.product.service.ProductCategoryBloomService;
import com.example.ShoppingSystem.product.service.PublicProductDetailCacheService;
import com.example.ShoppingSystem.product.service.PublicProductCategoryRelationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminProductCategoryService {
    public List<AdminProductCategoryTreeNodeResponse> tree();

    public List<AdminProductCategoryTreeNodeResponse> tree(String keyword);

    public AdminProductCategoryTreeNodeResponse create(AdminProductCategoryCreateRequest request);

    public AdminProductCategoryTreeNodeResponse update(Long id, AdminProductCategoryUpdateRequest request);

    public AdminProductCategoryTreeNodeResponse changeStatus(Long id, AdminProductCategoryStatusRequest request);

    public AdminProductCategoryBatchDisableResponse batchDisable(AdminProductCategoryBatchDisableRequest request);

    public void delete(Long id);
}
