package com.example.ShoppingSystem.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.order.dto.OrderApiResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackReceivedResponse;
import com.example.ShoppingSystem.order.dto.OrderPaymentCallbackRequest;
import com.example.ShoppingSystem.order.service.PaymentCallbackReceiveService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "支付回调", description = "订单支付回调接收接口")
@RestController
@RequestMapping("/shopping/api/payments/callback")
public class OrderPaymentCallbackController {

    private final PaymentCallbackReceiveService paymentCallbackReceiveService;

    public OrderPaymentCallbackController(PaymentCallbackReceiveService paymentCallbackReceiveService) {
        this.paymentCallbackReceiveService = paymentCallbackReceiveService;
    }

    @Operation(summary = "接收支付成功回调")
    @PostMapping("/success")
    public OrderApiResponse<OrderPaymentCallbackReceivedResponse> success(@RequestBody(required = false) OrderPaymentCallbackRequest request) {
        return OrderApiResponse.ok(
                "ORDER_PAYMENT_CALLBACK_RECEIVED",
                paymentCallbackReceiveService.receive(request)
        );
    }
}
