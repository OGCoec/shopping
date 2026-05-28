package com.example.ShoppingSystem.product.service;

import com.example.ShoppingSystem.mapper.product.ProductSpuMapper;
import com.example.ShoppingSystem.product.dto.PublicProductDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class PublicProductDetailService {

    private final ProductSpuMapper productSpuMapper;
    private final PublicProductSpuAssembler assembler;
    private final PublicProductDetailCacheService detailCacheService;

    public PublicProductDetailService(ProductSpuMapper productSpuMapper,
                                      PublicProductSpuAssembler assembler,
                                      PublicProductDetailCacheService detailCacheService) {
        this.productSpuMapper = productSpuMapper;
        this.assembler = assembler;
        this.detailCacheService = detailCacheService;
    }

    public PublicProductDetailResponse detail(String id) {
        Long spuId = normalizeProductId(id);
        return detailCacheService.getDetail(spuId, () -> findDetail(spuId));
    }

    private PublicProductDetailResponse findDetail(Long spuId) {
        Map<String, Object> row = productSpuMapper.findActivePublicSpuDetailById(spuId);
        if (row == null || row.isEmpty()) {
            return null;
        }
        PublicProductDetailResponse detail = assembler.toDetailResponse(row);
        return detail.id() == null || detail.id() <= 0 ? null : detail;
    }

    private Long normalizeProductId(String id) {
        String value = id == null ? "" : id.trim();
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID is invalid.");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID is invalid.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID is invalid.");
        }
    }
}
