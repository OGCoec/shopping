package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.rabbit.OrderClosingFinalizeCheckMessage;
import com.example.ShoppingSystem.order.rabbit.OrderExpireMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderClosingFinalizeCheckService {

    private static final Logger log = LoggerFactory.getLogger(OrderClosingFinalizeCheckService.class);

    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final PaymentCallbackPendingMarkerService paymentCallbackPendingMarkerService;
    private final OrderExpireService orderExpireService;
    private final OrderExpireMessagePublisher orderExpireMessagePublisher;

    public OrderClosingFinalizeCheckService(OrderRedisSnapshotService orderRedisSnapshotService,
                                            PaymentCallbackPendingMarkerService paymentCallbackPendingMarkerService,
                                            OrderExpireService orderExpireService,
                                            OrderExpireMessagePublisher orderExpireMessagePublisher) {
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.paymentCallbackPendingMarkerService = paymentCallbackPendingMarkerService;
        this.orderExpireService = orderExpireService;
        this.orderExpireMessagePublisher = orderExpireMessagePublisher;
    }

    public void check(OrderClosingFinalizeCheckMessage message) {
        String orderNo = normalize(message == null ? null : message.orderNo());
        if (orderNo.isEmpty()) {
            log.warn("[Order] invalid closing finalize check message skipped, message={}", message);
            return;
        }
        OrderRedisSnapshot snapshot = orderRedisSnapshotService.findSnapshot(orderNo).orElse(null);
        if (snapshot == null) {
            continueCheckOrFinalize(message, orderNo, "redis-missing");
            return;
        }
        String status = normalizedStatus(OrderRowMapper.text(snapshot.order(), "status"));
        if (!OrderStatus.CLOSING.equals(status)) {
            if (log.isDebugEnabled()) {
                log.debug("[Order] closing finalize check skipped, orderNo={}, status={}", orderNo, status);
            }
            return;
        }
        if (requeueWhenCallbackReceived(message, orderNo, "closing-callback-received")) {
            return;
        }
        continueCheckOrFinalize(message, orderNo, "closing");
    }

    private boolean requeueWhenCallbackReceived(OrderClosingFinalizeCheckMessage message, String orderNo, String reason) {
        if (!paymentCallbackPendingMarkerService.hasReceived(orderNo)) {
            return false;
        }
        if (orderExpireMessagePublisher.publishClosingFinalizeCallbackRetry(message)) {
            if (log.isDebugEnabled()) {
                log.debug("[Order] closing finalize check requeued, orderNo={}, reason={}", orderNo, reason);
            }
            return true;
        }
        return false;
    }

    private void continueCheckOrFinalize(OrderClosingFinalizeCheckMessage message, String orderNo, String reason) {
        if (orderExpireMessagePublisher.publishNextClosingFinalizeCheck(message)) {
            if (log.isDebugEnabled()) {
                log.debug("[Order] closing finalize check requeued, orderNo={}, reason={}", orderNo, reason);
            }
            return;
        }
        orderExpireService.finalizeClosing(orderNo);
    }

    private String normalizedStatus(String status) {
        String value = normalize(status);
        return value.isEmpty() ? "UNKNOWN" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
