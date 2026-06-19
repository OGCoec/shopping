package com.example.ShoppingSystem.order.service;

import java.math.BigDecimal;

public record OrderSkuSnapshot(byte[] skuId,
                               String skuIdText,
                               Long spuId,
                               Long categoryId,
                               String skuCode,
                               String skuName,
                               String specJson,
                               String skuImageUrl,
                               BigDecimal priceYuan,
                               boolean pointExchangeEnabled,
                               Long pointExchangePoints,
                               boolean hotSku) {
}
