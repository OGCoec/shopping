package com.example.ShoppingSystem.ai.dto;

public record AiModelResponse(String modelKey,
                              String providerModelName,
                              String displayName,
                              boolean enabled,
                              boolean defaultModel,
                              int contextTokens,
                              int outputReserveTokens,
                              double temperature) {
}
