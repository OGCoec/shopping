package com.example.ShoppingSystem.order.service.impl.OrderPreviewService;

import com.example.ShoppingSystem.order.dto.OrderPreviewRequest;
import com.example.ShoppingSystem.order.dto.OrderPreviewResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.example.ShoppingSystem.order.service.OrderPreviewService;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderCouponService;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderSkuService;
import com.example.ShoppingSystem.order.service.OrderSkuSnapshot;
@Service
public class OrderPreviewServiceImpl implements OrderPreviewService {

    private final OrderSkuService orderSkuService;
    private final OrderCouponService orderCouponService;

    public OrderPreviewServiceImpl(OrderSkuService orderSkuService,
                               OrderCouponService orderCouponService) {
        this.orderSkuService = orderSkuService;
        this.orderCouponService = orderCouponService;
    }

    public OrderPreviewResponse preview(Long userId, OrderPreviewRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        int quantity = normalizeQuantity(request == null ? null : request.quantity());
        OrderSkuSnapshot sku = orderSkuService.loadActiveSku(request == null ? null : request.skuId(), now);
        BigDecimal totalAmount = OrderAmountCalculator.lineAmount(sku.priceYuan(), quantity);
        long linePoints = linePoints(sku, quantity);
        OrderCouponService.CouponOptions couponOptions = orderCouponService.couponOptions(
                userId,
                sku,
                totalAmount,
                request == null ? null : request.selectedUserCouponId(),
                now
        );
        BigDecimal discountAmount = couponOptions.selectedDiscountAmountYuan();
        BigDecimal payAmount = OrderAmountCalculator.money(totalAmount.subtract(discountAmount));
        return new OrderPreviewResponse(
                sku.skuIdText(),
                sku.spuId(),
                sku.skuName(),
                sku.skuImageUrl(),
                quantity,
                sku.priceYuan(),
                totalAmount,
                sku.pointExchangeEnabled(),
                sku.pointExchangePoints(),
                linePoints,
                sku.hotSku(),
                couponOptions.selectedUserCouponId(),
                totalAmount,
                discountAmount,
                payAmount,
                couponOptions.availableCoupons(),
                couponOptions.unavailableCoupons()
        );
    }

    private long linePoints(OrderSkuSnapshot sku, int quantity) {
        if (sku == null || !sku.pointExchangeEnabled()) {
            return 0L;
        }
        long points = sku.pointExchangePoints() == null ? 0L : sku.pointExchangePoints();
        return Math.max(0L, points) * Math.max(0, quantity);
    }

    private int normalizeQuantity(Integer rawQuantity) {
        if (rawQuantity == null || rawQuantity <= 0) {
            throw new OrderServiceException("ORDER_QUANTITY_INVALID", "Quantity is invalid.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return rawQuantity;
    }
}
