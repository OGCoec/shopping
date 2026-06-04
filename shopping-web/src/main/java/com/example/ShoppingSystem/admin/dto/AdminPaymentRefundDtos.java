package com.example.ShoppingSystem.admin.dto;

public final class AdminPaymentRefundDtos {

    private AdminPaymentRefundDtos() {
    }

    public record AdminRefundApproveRequest(Long version,
                                            String adminRemark,
                                            String userMessage) {
    }

    public record AdminRefundRejectRequest(Long version,
                                           String rejectReason,
                                           String adminRemark) {
    }

    public record AdminRefundMarkRefundedRequest(Long version,
                                                 String refundProofNo,
                                                 String refundProofUrl,
                                                 String adminRemark) {
    }

    public record AdminRefundDispatchResponse(int claimedCount,
                                              int writtenCount) {
    }
}
