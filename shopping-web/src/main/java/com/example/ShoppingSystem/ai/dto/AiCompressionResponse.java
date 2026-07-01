package com.example.ShoppingSystem.ai.dto;

import java.util.List;

public record AiCompressionResponse(String summary,
                                    List<AiChatMessage> retainedMessages) {
}
