package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackReceivedResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackRequest;
public interface PaymentCallbackReceiveService {
    public OrderPaymentCallbackReceivedResponse receive(OrderPaymentCallbackRequest request);
}
