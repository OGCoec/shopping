package com.example.ShoppingSystem.ai.dto;

import java.util.List;

public record AiModelsResponse(String defaultModelKey,
                               int contextHardLimitTokens,
                               int compressionTriggerTokens,
                               List<AiModelResponse> models) {
}
