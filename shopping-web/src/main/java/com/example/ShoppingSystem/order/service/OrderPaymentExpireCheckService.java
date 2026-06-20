package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.example.ShoppingSystem.order.rabbit.OrderPaymentExpireCheckMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.Map;

public interface OrderPaymentExpireCheckService {
    public void check(OrderPaymentExpireCheckMessage message);
}
