package com.example.ShoppingSystem.admin.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdminProductSkuDeleteResponse(@JsonSerialize(using = ToStringSerializer.class) Long spuId,
                                            String skuId,
                                            boolean deleted,
                                            int cleanupImageCount) {
}
