package com.example.ShoppingSystem.order.service.impl.OrderCardSecretQueryService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderCardSecretDeliveryMapper;
import com.example.ShoppingSystem.order.dto.OrderCardSecretItemResponse;
import com.example.ShoppingSystem.order.dto.OrderCardSecretResponse;
import com.example.ShoppingSystem.order.dto.OrderCardSecretValueResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.ShoppingSystem.order.service.OrderCardSecretQueryService;
import com.example.ShoppingSystem.order.service.OrderCardSecretCryptoService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderStatus;
@Service
public class OrderCardSecretQueryServiceImpl implements OrderCardSecretQueryService {

    public static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";
    public static final String DELIVERY_STATUS_PENDING = "PENDING";
    private static final String VERSIONED_SECRET_PREFIX = "CARD_SECRET_V1|";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private final OrderCardSecretDeliveryMapper deliveryMapper;
    private final OrderCardSecretCryptoService cryptoService;
    private final OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor;

    public OrderCardSecretQueryServiceImpl(OrderCardSecretDeliveryMapper deliveryMapper,
                                       OrderCardSecretCryptoService cryptoService,
                                       OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor) {
        this.deliveryMapper = deliveryMapper;
        this.cryptoService = cryptoService;
        this.orderReadReplicaQueryExecutor = orderReadReplicaQueryExecutor;
    }

    public OrderCardSecretResponse getForUser(Long userId, String orderNo) {
        if (userId == null || userId <= 0) {
            throw new OrderServiceException("ORDER_AUTH_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        return orderReadReplicaQueryExecutor.query(() -> getForUserFromDb(userId, normalizedOrderNo));
    }

    private OrderCardSecretResponse getForUserFromDb(Long userId, String normalizedOrderNo) {
        OrderAndItems orderAndItems = loadOrderAndItems(userId, normalizedOrderNo);
        if (!OrderStatus.PAID.equals(orderAndItems.orderStatus())) {
            throw new OrderServiceException(
                    "ORDER_CARD_SECRET_ORDER_NOT_PAID",
                    "Only paid orders can view card secrets.",
                    HttpStatus.CONFLICT
            );
        }

        List<Map<String, Object>> deliveredRows = deliveryMapper.listDeliveredSecretsForUserOrder(userId, normalizedOrderNo);
        Map<String, List<OrderCardSecretValueResponse>> secretsBySkuId = secretsBySkuId(deliveredRows);
        List<OrderCardSecretItemResponse> items = new ArrayList<>(orderAndItems.items().size());
        int requiredCount = 0;
        int deliveredCount = 0;
        for (Map<String, Object> item : orderAndItems.items()) {
            String skuIdHex = normalizeHex(OrderRowMapper.text(item, "skuId"));
            if (skuIdHex.isBlank()) {
                continue;
            }
            int quantity = OrderRowMapper.intValue(item, "quantity", 0);
            List<OrderCardSecretValueResponse> secrets = secretsBySkuId.getOrDefault(skuIdHex, List.of());
            requiredCount += Math.max(quantity, 0);
            deliveredCount += secrets.size();
            items.add(new OrderCardSecretItemResponse(
                    HybridIdCodec.hexToBase62(skuIdHex),
                    OrderRowMapper.text(item, "skuName"),
                    quantity,
                    secrets.size(),
                    secrets
            ));
        }
        String deliveryStatus = deliveredCount >= requiredCount
                ? DELIVERY_STATUS_DELIVERED
                : DELIVERY_STATUS_PENDING;
        return new OrderCardSecretResponse(
                normalizedOrderNo,
                orderAndItems.orderStatus(),
                deliveryStatus,
                requiredCount,
                deliveredCount,
                items
        );
    }

    private OrderAndItems loadOrderAndItems(Long userId, String orderNo) {
        List<Map<String, Object>> rows = deliveryMapper.listOrderItemsForUserOrder(userId, orderNo);
        if (rows == null || rows.isEmpty()) {
            throw new OrderServiceException(
                    "ORDER_CARD_SECRET_ORDER_NOT_FOUND",
                    "Order does not exist.",
                    HttpStatus.NOT_FOUND
            );
        }
        String orderStatus = OrderRowMapper.text(rows.get(0), "orderStatus");
        List<Map<String, Object>> items = rows.stream()
                .filter(row -> !normalizeHex(OrderRowMapper.text(row, "skuId")).isBlank())
                .toList();
        return new OrderAndItems(orderStatus, items);
    }

    private Map<String, List<OrderCardSecretValueResponse>> secretsBySkuId(List<Map<String, Object>> rows) {
        Map<String, List<OrderCardSecretValueResponse>> result = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            String skuIdHex = normalizeHex(OrderRowMapper.text(row, "skuId"));
            if (skuIdHex.isBlank()) {
                continue;
            }
            String secret = normalizeDisplaySecret(cryptoService.decrypt(
                    OrderRowMapper.text(row, "secretCiphertext"),
                    OrderRowMapper.text(row, "secretNonce"),
                    OrderRowMapper.text(row, "secretKeyVersion")
            ));
            result.computeIfAbsent(skuIdHex, ignored -> new ArrayList<>())
                    .add(new OrderCardSecretValueResponse(
                            OrderRowMapper.text(row, "cardSecretId"),
                            secret,
                            OrderRowMapper.offsetDateTime(row, "deliveredAt")
                    ));
        }
        return result;
    }

    private String normalizeDisplaySecret(String secret) {
        String value = secret == null ? "" : secret;
        String versioned = stripVersionedUuidPrefix(value);
        if (!versioned.equals(value)) {
            return versioned;
        }
        String leadingColon = stripLeadingUuid(value, ':');
        if (!leadingColon.equals(value)) {
            return leadingColon;
        }
        String leadingPipe = stripLeadingUuid(value, '|');
        if (!leadingPipe.equals(value)) {
            return leadingPipe;
        }
        String trailingColon = stripTrailingUuid(value, ':');
        if (!trailingColon.equals(value)) {
            return trailingColon;
        }
        return stripTrailingUuid(value, '|');
    }

    private String stripVersionedUuidPrefix(String value) {
        if (!value.startsWith(VERSIONED_SECRET_PREFIX)) {
            return value;
        }
        String rest = value.substring(VERSIONED_SECRET_PREFIX.length());
        int separator = rest.indexOf('|');
        if (separator <= 0) {
            return value;
        }
        String uuid = rest.substring(0, separator);
        return UUID_PATTERN.matcher(uuid).matches() ? rest.substring(separator + 1) : value;
    }

    private String stripLeadingUuid(String value, char separator) {
        int index = value.indexOf(separator);
        if (index <= 0) {
            return value;
        }
        String uuid = value.substring(0, index);
        return UUID_PATTERN.matcher(uuid).matches() ? value.substring(index + 1) : value;
    }

    private String stripTrailingUuid(String value, char separator) {
        int index = value.lastIndexOf(separator);
        if (index <= 0 || index >= value.length() - 1) {
            return value;
        }
        String uuid = value.substring(index + 1);
        return UUID_PATTERN.matcher(uuid).matches() ? value.substring(0, index) : value;
    }

    private String normalizeOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty() || value.length() > 64) {
            throw new OrderServiceException("ORDER_NO_INVALID", "Order number is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeHex(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\\x") || normalized.startsWith("\\X")) {
            normalized = normalized.substring(2);
        }
        return normalized.matches("^[0-9A-Fa-f]{32}$") ? normalized.toLowerCase() : "";
    }

    private record OrderAndItems(String orderStatus,
                                 List<Map<String, Object>> items) {
    }
}
