package com.example.ShoppingSystem.admin.dto;

public record AdminCardSecretImportResponse(Long spuId,
                                            String skuId,
                                            String batchNo,
                                            int receivedLineCount,
                                            int blankLineCount,
                                            int duplicateInRequestCount,
                                            int uniqueCandidateCount,
                                            int insertedCount,
                                            int duplicateInDbCount,
                                            int failedCount,
                                            int stockIncrementCount,
                                            int skuStockQuantity) {
}
