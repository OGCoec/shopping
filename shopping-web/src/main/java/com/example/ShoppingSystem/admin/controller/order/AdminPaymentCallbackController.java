package com.example.ShoppingSystem.admin.controller.order;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminPaymentCallbackDtos.AdminPaymentCallbackDispatchResponse;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentCallbackInboxResponse;
import com.example.ShoppingSystem.order.service.PaymentCallbackDispatchService;
import com.example.ShoppingSystem.order.service.PaymentCallbackInboxQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/payment-callbacks")
public class AdminPaymentCallbackController {

    private final PaymentCallbackInboxQueryService paymentCallbackInboxQueryService;
    private final PaymentCallbackDispatchService paymentCallbackDispatchService;

    public AdminPaymentCallbackController(PaymentCallbackInboxQueryService paymentCallbackInboxQueryService,
                                          PaymentCallbackDispatchService paymentCallbackDispatchService) {
        this.paymentCallbackInboxQueryService = paymentCallbackInboxQueryService;
        this.paymentCallbackDispatchService = paymentCallbackDispatchService;
    }

    @GetMapping
    public AdminApiResponse<PaymentCallbackInboxPageResponse> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "callbackNo", required = false) String callbackNo,
            @RequestParam(value = "externalTradeNo", required = false) String externalTradeNo,
            @RequestParam(value = "resultOutcome", required = false) String resultOutcome) {
        return AdminApiResponse.ok(paymentCallbackInboxQueryService.page(
                page,
                pageSize,
                status,
                orderNo,
                callbackNo,
                externalTradeNo,
                resultOutcome
        ));
    }

    @GetMapping("/{callbackNo}")
    public AdminApiResponse<PaymentCallbackInboxResponse> detail(@PathVariable String callbackNo) {
        return AdminApiResponse.ok(paymentCallbackInboxQueryService.detail(callbackNo));
    }

    @PostMapping("/dispatch")
    public AdminApiResponse<AdminPaymentCallbackDispatchResponse> dispatch(@RequestParam(value = "limit", required = false) Integer limit) {
        PaymentCallbackDispatchService.DispatchSummary summary = paymentCallbackDispatchService.dispatchAvailable(limit);
        return AdminApiResponse.ok(new AdminPaymentCallbackDispatchResponse(
                summary.claimedCount(),
                summary.inboxWrittenCount(),
                summary.refundCount(),
                summary.failedCount()
        ));
    }
}
