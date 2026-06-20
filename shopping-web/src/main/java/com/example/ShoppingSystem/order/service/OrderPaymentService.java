package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.dto.OrderPaymentRequest;
import com.example.ShoppingSystem.order.dto.OrderPaymentResponse;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface OrderPaymentService {
    public OrderPaymentResponse pay(Long userId, String rawOrderNo, OrderPaymentRequest request);
}
