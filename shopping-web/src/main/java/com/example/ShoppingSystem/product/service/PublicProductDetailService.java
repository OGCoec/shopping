package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.config.datasource.ProductReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

public interface PublicProductDetailService {
    public PublicProductDetailResponse detail(String id);
}
