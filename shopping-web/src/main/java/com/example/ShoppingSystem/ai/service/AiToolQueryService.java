package com.example.ShoppingSystem.ai.service;

import com.example.ShoppingSystem.ai.dto.AiToolIntent;
import com.example.ShoppingSystem.ai.dto.AiToolResult;

public interface AiToolQueryService {

    public AiToolResult execute(Long userId, AiToolIntent intent);
}
