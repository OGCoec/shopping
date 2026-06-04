package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "shopping.admin.coupon-template-cdc", name = "enabled", havingValue = "true")
public class AdminCouponTemplateCdcConsumer {

    private final ObjectMapper objectMapper;
    private final AdminCouponTemplateIndexService indexService;

    public AdminCouponTemplateCdcConsumer(ObjectMapper objectMapper,
                                          AdminCouponTemplateIndexService indexService) {
        this.objectMapper = objectMapper;
        this.indexService = indexService;
    }

    @KafkaListener(
            topics = "${shopping.admin.coupon-template-cdc.topic:shopping.public.coupon_template}",
            groupId = "${shopping.admin.coupon-template-cdc.group-id:shopping-coupon-template-es-indexer}"
    )
    public void handleCouponTemplateChange(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = payload(root);
            String operation = text(payload.path("op"));
            JsonNode row = "d".equals(operation) ? payload.path("before") : payload.path("after");
            String couponTemplateId = couponTemplateId(row.path("id"));
            if (couponTemplateId.isBlank()) {
                log.debug("Coupon template CDC event ignored because id is empty, op={}", operation);
                return;
            }
            if ("d".equals(operation)) {
                indexService.deleteCouponTemplates(List.of(couponTemplateId));
                return;
            }
            indexService.syncCouponTemplates(List.of(couponTemplateId));
        } catch (Exception e) {
            log.warn("Coupon template CDC event handling failed.", e);
            throw new IllegalStateException("Coupon template CDC event handling failed.", e);
        }
    }

    private JsonNode payload(JsonNode root) {
        JsonNode payload = root == null ? null : root.get("payload");
        return payload != null && payload.isObject() ? payload : root;
    }

    private String couponTemplateId(JsonNode idNode) {
        String value = text(idNode);
        if (value.isBlank()) {
            return "";
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length == 16) {
                return HybridIdCodec.toBase62(bytes);
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return HybridIdCodec.toBase62FromDatabaseValue(value);
        } catch (IllegalArgumentException ignored) {
            return value.matches(HybridIdCodec.BASE62_PATTERN) ? value : "";
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText().trim() : String.valueOf(node).trim();
    }
}
