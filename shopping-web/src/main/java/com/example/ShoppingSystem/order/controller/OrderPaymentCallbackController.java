package com.example.ShoppingSystem.order.controller;

import com.example.ShoppingSystem.order.dto.OrderApiResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackReceivedResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackRequest;
import com.example.ShoppingSystem.order.service.PaymentCallbackReceiveService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/api/payments/callback")
public class OrderPaymentCallbackController {

    private final PaymentCallbackReceiveService paymentCallbackReceiveService;

    public OrderPaymentCallbackController(PaymentCallbackReceiveService paymentCallbackReceiveService) {
        this.paymentCallbackReceiveService = paymentCallbackReceiveService;
    }

    @PostMapping("/success")
    public OrderApiResponse<OrderPaymentCallbackReceivedResponse> success(@RequestBody(required = false) OrderPaymentCallbackRequest request) {
        return OrderApiResponse.ok(
                "ORDER_PAYMENT_CALLBACK_RECEIVED",
                paymentCallbackReceiveService.receive(request)
        );
    }
}
