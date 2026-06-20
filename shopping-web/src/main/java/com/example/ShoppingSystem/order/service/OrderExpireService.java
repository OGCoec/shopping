package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import com.example.ShoppingSystem.order.rabbit.OrderExpireRabbitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface OrderExpireService {
    public boolean startClosing(String orderNo);

    public boolean finalizeClosing(String orderNo);
}
