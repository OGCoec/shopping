package com.example.ShoppingSystem.order.service;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxResponse;
public interface PaymentCallbackInboxQueryService {
    public PaymentCallbackInboxPageResponse page(Integer rawPage,
                                                 Integer rawPageSize,
                                                 String rawStatus,
                                                 String rawOrderNo,
                                                 String rawCallbackNo,
                                                 String rawExternalTradeNo,
                                                 String rawResultOutcome);

    public PaymentCallbackInboxResponse detail(String callbackNo);
}
