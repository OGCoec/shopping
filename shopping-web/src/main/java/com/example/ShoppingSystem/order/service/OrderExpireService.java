package com.example.ShoppingSystem.order.service;

public interface OrderExpireService {
    public boolean startClosing(String orderNo);

    public boolean finalizeClosing(String orderNo);
}
