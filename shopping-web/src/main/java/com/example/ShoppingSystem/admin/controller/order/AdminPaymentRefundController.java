package com.example.ShoppingSystem.admin.controller.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminPaymentRefundDtos.AdminRefundApproveRequest;
import com.example.ShoppingSystem.admin.dto.AdminPaymentRefundDtos.AdminRefundDispatchResponse;
import com.example.ShoppingSystem.admin.dto.AdminPaymentRefundDtos.AdminRefundMarkRefundedRequest;
import com.example.ShoppingSystem.admin.dto.AdminPaymentRefundDtos.AdminRefundRejectRequest;
import com.example.ShoppingSystem.order.dto.PaymentRefundPageResponse;
import com.example.ShoppingSystem.order.dto.PaymentRefundResponse;
import com.example.ShoppingSystem.order.service.PaymentRefundDispatchService;
import com.example.ShoppingSystem.order.service.PaymentRefundService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台退款管理", description = "后台支付退款管理接口")
@RestController
@RequestMapping("/shopping/admin/api/refunds")
public class AdminPaymentRefundController {

    private static final Long SYSTEM_ADMIN_ID = 1L;

    private final PaymentRefundService paymentRefundService;
    private final PaymentRefundDispatchService paymentRefundDispatchService;

    public AdminPaymentRefundController(PaymentRefundService paymentRefundService,
                                        PaymentRefundDispatchService paymentRefundDispatchService) {
        this.paymentRefundService = paymentRefundService;
        this.paymentRefundDispatchService = paymentRefundDispatchService;
    }

    @Operation(summary = "分页查询后台退款单")
    @GetMapping
    public AdminApiResponse<PaymentRefundPageResponse> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "refundNo", required = false) String refundNo,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "reasonCode", required = false) String reasonCode) {
        return AdminApiResponse.ok(paymentRefundService.pageForAdmin(
                page,
                pageSize,
                status,
                orderNo,
                refundNo,
                source,
                reasonCode
        ));
    }

    @Operation(summary = "查询后台退款单详情")
    @GetMapping("/{refundNo}")
    public AdminApiResponse<PaymentRefundResponse> detail(@PathVariable String refundNo) {
        return AdminApiResponse.ok(paymentRefundService.detailForAdmin(refundNo));
    }

    @Operation(summary = "审核通过退款申请")
    @PostMapping("/{refundNo}/approve")
    public AdminApiResponse<PaymentRefundResponse> approve(@PathVariable String refundNo,
                                                           @RequestBody(required = false) AdminRefundApproveRequest request) {
        return AdminApiResponse.ok(paymentRefundService.approve(
                refundNo,
                request == null ? null : request.version(),
                SYSTEM_ADMIN_ID,
                request == null ? null : request.adminRemark(),
                request == null ? null : request.userMessage()
        ));
    }

    @Operation(summary = "驳回退款申请")
    @PostMapping("/{refundNo}/reject")
    public AdminApiResponse<PaymentRefundResponse> reject(@PathVariable String refundNo,
                                                          @RequestBody(required = false) AdminRefundRejectRequest request) {
        return AdminApiResponse.ok(paymentRefundService.reject(
                refundNo,
                request == null ? null : request.version(),
                SYSTEM_ADMIN_ID,
                request == null ? null : request.rejectReason(),
                request == null ? null : request.adminRemark()
        ));
    }

    @Operation(summary = "标记退款已完成")
    @PostMapping("/{refundNo}/mark-refunded")
    public AdminApiResponse<PaymentRefundResponse> markRefunded(@PathVariable String refundNo,
                                                                @RequestBody(required = false) AdminRefundMarkRefundedRequest request) {
        return AdminApiResponse.ok(paymentRefundService.markRefunded(
                refundNo,
                request == null ? null : request.version(),
                SYSTEM_ADMIN_ID,
                request == null ? null : request.refundProofNo(),
                request == null ? null : request.refundProofUrl(),
                request == null ? null : request.adminRemark()
        ));
    }

    @Operation(summary = "派发退款处理任务")
    @PostMapping("/dispatch")
    public AdminApiResponse<AdminRefundDispatchResponse> dispatch(@RequestParam(value = "limit", required = false) Integer limit) {
        PaymentRefundDispatchService.DispatchSummary summary = paymentRefundDispatchService.dispatchAvailable(limit);
        return AdminApiResponse.ok(new AdminRefundDispatchResponse(summary.claimedCount(), summary.writtenCount()));
    }
}
