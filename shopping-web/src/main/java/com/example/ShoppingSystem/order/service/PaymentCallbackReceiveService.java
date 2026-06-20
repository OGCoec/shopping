package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackReceivedResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public interface PaymentCallbackReceiveService {
    public OrderPaymentCallbackReceivedResponse receive(OrderPaymentCallbackRequest request);
}
