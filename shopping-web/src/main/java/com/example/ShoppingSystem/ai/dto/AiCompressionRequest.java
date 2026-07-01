package com.example.ShoppingSystem.ai.dto;

import java.util.List;

public record AiCompressionRequest(String modelKey,
                                   List<AiChatMessage> messages,
                                   String summary) {
}
