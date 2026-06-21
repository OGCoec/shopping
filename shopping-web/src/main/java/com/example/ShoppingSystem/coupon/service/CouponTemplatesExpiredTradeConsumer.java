package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import com.example.ShoppingSystem.outbox.annotation.IdempotentConsumer;
import com.example.ShoppingSystem.outbox.fault.FaultInjector;
import com.example.ShoppingSystem.outbox.couponexpire.CouponTemplatesExpiredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CouponTemplatesExpiredTradeConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponTemplatesExpiredTradeConsumer.class);

    private final UserCouponMapper userCouponMapper;

    private final FaultInjector faultInjector;
    public CouponTemplatesExpiredTradeConsumer(UserCouponMapper userCouponMapper, FaultInjector faultInjector) {
        this.userCouponMapper = userCouponMapper;
        this.faultInjector = faultInjector;
    }

    @RabbitListener(
            queues = "#{couponTemplatesExpiredTradeQueue.name}",
            containerFactory = "couponTemplatesExpiredRabbitListenerContainerFactory"
    )
    @IdempotentConsumer(route = DataSourceRoute.TRADE, consumer = "coupon-templates-expired-trade",
            eventId = "#message.eventId", transactional = true)
    public void consume(CouponTemplatesExpiredMessage message) {
        faultInjector.maybeFail("coupon-templates-expired-trade", message == null ? null : message.getLoadtestFault());
                List<String> templateIdHexes = normalizeTemplateIds(message == null ? null : message.getTemplateIdHexes());
        if (message == null || message.getEventId() == null || message.getEventId().isBlank() || templateIdHexes.isEmpty()) {
            log.warn("[CouponTemplatesExpired] invalid message skipped, message={}", message);
            return;
        }
        int expired = userCouponMapper.expireUnusedCouponsByTemplateIds(templateIdHexes);
        log.info("[CouponTemplatesExpired] trade user coupons expired, templates={}, userCoupons={}, eventId={}",
                templateIdHexes.size(), expired, message.getEventId());
    }

    private List<String> normalizeTemplateIds(List<String> templateIdHexes) {
        if (templateIdHexes == null || templateIdHexes.isEmpty()) {
            return List.of();
        }
        return templateIdHexes.stream()
                .filter(value -> value != null && value.matches("^[0-9A-Fa-f]{32}$"))
                .map(String::toLowerCase)
                .distinct()
                .sorted()
                .toList();
    }
}
