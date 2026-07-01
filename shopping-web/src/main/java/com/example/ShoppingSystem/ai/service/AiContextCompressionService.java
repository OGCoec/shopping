package com.example.ShoppingSystem.ai.service;

import com.example.ShoppingSystem.ai.dto.AiCompressionRequest;
import com.example.ShoppingSystem.ai.dto.AiCompressionResponse;

public interface AiContextCompressionService {

    public AiCompressionResponse compress(AiCompressionRequest request);
}
