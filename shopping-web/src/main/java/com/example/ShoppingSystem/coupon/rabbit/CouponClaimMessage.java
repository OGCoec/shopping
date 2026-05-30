package com.example.ShoppingSystem.coupon.rabbit;

import lombok.Data;

@Data
public class CouponClaimMessage {

    private String claimId;
    private String couponId;
    private String userCouponId;
    private Long userId;
    private Long validStartAtEpochMs;
    private Long validEndAtEpochMs;
    private Long createdAtEpochMilli;
    private int retryCount;
    private String lastError;

    public CouponClaimMessage nextRetry(String error) {
        CouponClaimMessage message = copy();
        message.setRetryCount(retryCount + 1);
        message.setLastError(error);
        return message;
    }

    public CouponClaimMessage markFailed(String error) {
        CouponClaimMessage message = copy();
        message.setLastError(error);
        return message;
    }

    private CouponClaimMessage copy() {
        CouponClaimMessage message = new CouponClaimMessage();
        message.setClaimId(claimId);
        message.setCouponId(couponId);
        message.setUserCouponId(userCouponId);
        message.setUserId(userId);
        message.setValidStartAtEpochMs(validStartAtEpochMs);
        message.setValidEndAtEpochMs(validEndAtEpochMs);
        message.setCreatedAtEpochMilli(createdAtEpochMilli);
        message.setRetryCount(retryCount);
        message.setLastError(lastError);
        return message;
    }
}
