package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.rabbit.OrderPaymentExpireCheckMessage;
public interface OrderPaymentExpireCheckService {
    public void check(OrderPaymentExpireCheckMessage message);
}
