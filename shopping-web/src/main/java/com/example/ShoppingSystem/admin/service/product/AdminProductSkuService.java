package com.example.ShoppingSystem.admin.service.product;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuCreateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSkuUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminProductSpuDetailSkuUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
public interface AdminProductSkuService {
    public record NormalizedSkuUpdate(String requestedId,
                                          String finalId,
                                          byte[] requestedIdBytes,
                                          byte[] finalIdBytes,
                                          Long spuId,
                                          String skuCode,
                                          String skuName,
                                          JsonNode specJson,
                                          JsonNode skuImageUrls,
                                          BigDecimal priceYuan,
                                          BigDecimal originalPriceYuan,
                                          Integer stockQuantity,
                                          String status) {
            public NormalizedSkuUpdate withSkuImageUrls(JsonNode nextSkuImageUrls) {
                return new NormalizedSkuUpdate(
                        requestedId,
                        finalId,
                        requestedIdBytes,
                        finalIdBytes,
                        spuId,
                        skuCode,
                        skuName,
                        specJson,
                        nextSkuImageUrls,
                        priceYuan,
                        originalPriceYuan,
                        stockQuantity,
                        status);
            }
        }

    public List<NormalizedSkuUpdate> normalizeSkuUpdates(Long spuId, List<AdminProductSpuDetailSkuUpdateRequest> rawSkus);

    public NormalizedSkuUpdate normalizeSkuCreate(Long spuId, AdminProductSkuCreateRequest rawSku);

    public NormalizedSkuUpdate normalizeSkuUpdate(Long spuId, String skuId, AdminProductSkuUpdateRequest rawSku);

    public String toSkuJson(List<NormalizedSkuUpdate> skus);
}
