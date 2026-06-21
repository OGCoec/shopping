package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.outbox.OutboxEventRequest;
import com.example.ShoppingSystem.outbox.annotation.OutboxEventCollector;
import com.example.ShoppingSystem.outbox.annotation.TransactionalOutbox;
import com.example.ShoppingSystem.outbox.couponexpire.CouponTemplatesExpiredMessage;
import com.example.ShoppingSystem.outbox.couponexpire.CouponTemplatesExpiredRouting;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

@Component
public class CouponTemplateExpireWriter {

    private final CouponTemplateMapper couponTemplateMapper;
    private final OutboxEventCollector outboxEventCollector;

    public CouponTemplateExpireWriter(CouponTemplateMapper couponTemplateMapper,
                                      OutboxEventCollector outboxEventCollector) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.outboxEventCollector = outboxEventCollector;
    }

    @TransactionalOutbox(DataSourceRoute.COUPON)
    public int expireTemplates() {
        List<String> templateIdHexes = normalizeTemplateIds(couponTemplateMapper.expireTemplates());
        if (templateIdHexes.isEmpty()) {
            return 0;
        }
        String fingerprint = fingerprint(templateIdHexes);
        String eventId = "coupon-templates-expired:" + fingerprint;
        CouponTemplatesExpiredMessage message = new CouponTemplatesExpiredMessage(
                eventId,
                templateIdHexes,
                OffsetDateTime.now().toInstant().toEpochMilli()
        );
        outboxEventCollector.register(new OutboxEventRequest(
                eventId,
                CouponTemplatesExpiredRouting.EVENT_TYPE,
                CouponTemplatesExpiredRouting.AGGREGATE_TYPE,
                fingerprint,
                CouponTemplatesExpiredRouting.EXCHANGE,
                CouponTemplatesExpiredRouting.ROUTING_KEY,
                message,
                eventId
        ));
        return templateIdHexes.size();
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

    private String fingerprint(List<String> templateIdHexes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String templateIdHex : templateIdHexes) {
                digest.update(templateIdHex.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available.", e);
        }
    }
}
