package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.rabbit.OrderClosingFinalizeCheckMessage;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface OrderClosingFinalizeCheckService {
    public void check(OrderClosingFinalizeCheckMessage message);
}
