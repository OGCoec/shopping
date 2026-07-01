package com.example.ShoppingSystem.order.service;

public interface PaymentCallbackPendingMarkerService {
    public void markReceived(String orderNo, String callbackNo);

    public boolean hasReceived(String orderNo);
}
