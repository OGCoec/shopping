package com.example.ShoppingSystem.ai.dto;

import java.util.List;

public record AiChatStreamRequest(String modelKey,
                                  List<AiChatMessage> messages,
                                  String summary,
                                  String clientConversationId) {
}
