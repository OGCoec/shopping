package com.example.ShoppingSystem.ai.service;

import com.example.ShoppingSystem.ai.dto.AiChatStreamRequest;
import com.example.ShoppingSystem.ai.dto.AiModelsResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {

    public AiModelsResponse models();

    public SseEmitter stream(Long userId, AiChatStreamRequest request);
}
