package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.rabbit.OrderClosingFinalizeCheckMessage;
public interface OrderClosingFinalizeCheckService {
    public void check(OrderClosingFinalizeCheckMessage message);
}
