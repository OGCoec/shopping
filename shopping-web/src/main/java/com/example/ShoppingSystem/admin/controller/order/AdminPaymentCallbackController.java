package com.example.ShoppingSystem.admin.controller.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "后台支付回调", description = "后台支付回调记录管理接口")
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

    @Operation(summary = "分页查询支付回调记录")
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

    @Operation(summary = "查询支付回调详情")
    @GetMapping("/{callbackNo}")
    public AdminApiResponse<PaymentCallbackInboxResponse> detail(@PathVariable String callbackNo) {
        return AdminApiResponse.ok(paymentCallbackInboxQueryService.detail(callbackNo));
    }

    @Operation(summary = "派发支付回调处理任务")
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
