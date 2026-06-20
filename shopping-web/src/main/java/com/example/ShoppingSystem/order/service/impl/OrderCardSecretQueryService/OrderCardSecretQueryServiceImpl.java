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

import com.example.ShoppingSystem.order.service.OrderCardSecretQueryService;
import com.example.ShoppingSystem.order.service.OrderCardSecretCryptoService;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderStatus;
@Service
public class OrderCardSecretQueryServiceImpl implements OrderCardSecretQueryService {

    public static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";
    public static final String DELIVERY_STATUS_PENDING = "PENDING";

    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderCardSecretDeliveryMapper deliveryMapper;
    private final OrderCardSecretCryptoService cryptoService;
    private final OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor;

    public OrderCardSecretQueryServiceImpl(OrderRedisSnapshotService orderRedisSnapshotService,
                                       OrderCardSecretDeliveryMapper deliveryMapper,
                                       OrderCardSecretCryptoService cryptoService,
                                       OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor) {
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.deliveryMapper = deliveryMapper;
        this.cryptoService = cryptoService;
        this.orderReadReplicaQueryExecutor = orderReadReplicaQueryExecutor;
    }

    public OrderCardSecretResponse getForUser(Long userId, String orderNo) {
        if (userId == null || userId <= 0) {
            throw new OrderServiceException("ORDER_AUTH_REQUIRED", "Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        String normalizedOrderNo = normalizeOrderNo(orderNo);
        return orderReadReplicaQueryExecutor.queryPrimary(() -> getForUserOnPrimary(userId, normalizedOrderNo));
    }

    private OrderCardSecretResponse getForUserOnPrimary(Long userId, String normalizedOrderNo) {
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
        OrderRedisSnapshot snapshot = orderRedisSnapshotService.findSnapshotForUser(orderNo, userId).orElse(null);
        if (snapshot != null) {
            return new OrderAndItems(
                    OrderRowMapper.text(snapshot.order(), "status"),
                    normalizeSnapshotItems(orderNo, userId, snapshot.items())
            );
        }
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

    private List<Map<String, Object>> normalizeSnapshotItems(String orderNo,
                                                            Long userId,
                                                            List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", orderNo);
            row.put("userId", userId);
            row.put("skuId", OrderRowMapper.text(item, "skuId"));
            row.put("skuName", OrderRowMapper.text(item, "skuName"));
            row.put("quantity", OrderRowMapper.intValue(item, "quantity", 0));
            rows.add(row);
        }
        return rows;
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
            String secret = cryptoService.decrypt(
                    OrderRowMapper.text(row, "secretCiphertext"),
                    OrderRowMapper.text(row, "secretNonce"),
                    OrderRowMapper.text(row, "secretKeyVersion")
            );
            result.computeIfAbsent(skuIdHex, ignored -> new ArrayList<>())
                    .add(new OrderCardSecretValueResponse(
                            OrderRowMapper.text(row, "cardSecretId"),
                            secret,
                            OrderRowMapper.offsetDateTime(row, "deliveredAt")
                    ));
        }
        return result;
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
