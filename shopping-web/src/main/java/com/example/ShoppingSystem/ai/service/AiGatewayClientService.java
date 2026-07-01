package com.example.ShoppingSystem.ai.service;

import com.example.ShoppingSystem.ai.config.AiProperties;
import com.example.ShoppingSystem.ai.dto.AiChatMessage;

import java.util.List;
import java.util.function.Consumer;

public interface AiGatewayClientService {

    public String complete(AiProperties.Model model,
                           List<AiChatMessage> messages,
                           double temperature,
                           int maxTokens);

    public void stream(AiProperties.Model model,
                       List<AiChatMessage> messages,
                       double temperature,
                       Consumer<String> chunkConsumer);
}
