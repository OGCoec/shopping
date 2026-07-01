package com.example.ShoppingSystem.ai.dto;

public record AiToolResult(AiToolIntentType intent,
                           String status,
                           String answer,
                           Object data) {
}
