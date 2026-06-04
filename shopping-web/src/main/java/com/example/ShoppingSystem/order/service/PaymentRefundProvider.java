package com.example.ShoppingSystem.order.service;

import java.util.List;

public interface PaymentRefundProvider {

    List<PaymentRefundDispatchResult> refund(List<PaymentRefundDispatchItem> items,
                                             int maxRetry,
                                             long retryBackoffBaseMillis);
}
