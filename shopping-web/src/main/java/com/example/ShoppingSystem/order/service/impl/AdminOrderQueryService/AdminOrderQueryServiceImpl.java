package com.example.ShoppingSystem.order.service.impl.AdminOrderQueryService;

import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminOrderDtos.AdminOrderPageResponse;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.ShoppingSystem.order.service.AdminOrderQueryService;
import com.example.ShoppingSystem.order.service.OrderAmountCalculator;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderStatus;
@Service
public class AdminOrderQueryServiceImpl implements AdminOrderQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SOURCE_REDIS = "REDIS";
    private static final String SOURCE_DB = "DB";
    private static final String SOURCE_MERGED = "MERGED";
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.CLOSING,
            OrderStatus.PAID,
            OrderStatus.CANCELLED,
            OrderStatus.CLOSED
    );

    private final OrderMapper orderMapper;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor;

    public AdminOrderQueryServiceImpl(OrderMapper orderMapper,
                                  OrderRedisSnapshotService orderRedisSnapshotService,
                                  OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor) {
        this.orderMapper = orderMapper;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderReadReplicaQueryExecutor = orderReadReplicaQueryExecutor;
    }

    public AdminOrderPageResponse page(Integer rawPage, Integer rawPageSize, String rawStatus, String rawOrderNo) {
        int page = rawPage == null || rawPage <= 0 ? DEFAULT_PAGE : rawPage;
        int pageSize = rawPageSize == null || rawPageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(rawPageSize, MAX_PAGE_SIZE);
        String status = normalizeStatus(rawStatus);
        String orderNo = normalizeOptionalOrderNo(rawOrderNo);
        if (orderNo != null) {
            return exactOrderPage(page, pageSize, status, orderNo);
        }

        long offset = (long) (page - 1) * pageSize;
        int candidateLimit = Math.max(page * pageSize, pageSize);
        List<AdminOrderListItemResponse> redisRecords = orderRedisSnapshotService.listAllSnapshots(candidateLimit)
                .stream()
                .map(snapshot -> listItem(snapshot.order(), snapshot.items(), SOURCE_REDIS))
                .filter(record -> status == null || status.equals(record.status()))
                .toList();
        AdminOrderDbPage dbPage = orderReadReplicaQueryExecutor.query(() -> new AdminOrderDbPage(
                orderMapper.countOrdersForAdmin(status, null),
                orderMapper.pageOrdersForAdmin(status, null, candidateLimit, 0)
                        .stream()
                        .map(row -> listItem(row, SOURCE_DB))
                        .toList()
        ));
        List<AdminOrderListItemResponse> dbRecords = dbPage.records();
        List<AdminOrderListItemResponse> merged = merge(redisRecords, dbRecords);
        List<AdminOrderListItemResponse> records = merged.stream()
                .skip(offset)
                .limit(pageSize)
                .toList();
        long dbTotal = dbPage.total();
        long redisOnlyCount = redisOnlyCount(redisRecords, dbRecords);
        return new AdminOrderPageResponse(page, pageSize, dbTotal + redisOnlyCount, records);
    }

    public AdminOrderDetailResponse detail(String rawOrderNo) {
        String orderNo = normalizeRequiredOrderNo(rawOrderNo);
        OrderRedisSnapshot snapshot = orderRedisSnapshotService.findSnapshot(orderNo).orElse(null);
        if (snapshot != null) {
            return detail(snapshot.order(), snapshot.items(), SOURCE_REDIS);
        }
        return orderReadReplicaQueryExecutor.query(() -> {
            Map<String, Object> order = orderMapper.findOrderByOrderNo(orderNo);
            if (order == null || order.isEmpty()) {
            throw new AdminServiceException("ADMIN_ORDER_NOT_FOUND", "订单不存在。", HttpStatus.NOT_FOUND);
            }
            List<Map<String, Object>> items = orderMapper.listOrderItems(orderNo);
            return detail(order, items, SOURCE_DB);
        });
    }

    private AdminOrderPageResponse exactOrderPage(int page, int pageSize, String status, String orderNo) {
        List<AdminOrderListItemResponse> redisRecords = orderRedisSnapshotService.findSnapshot(orderNo)
                .map(snapshot -> listItem(snapshot.order(), snapshot.items(), SOURCE_REDIS))
                .filter(record -> status == null || status.equals(record.status()))
                .map(List::of)
                .orElseGet(List::of);
        List<AdminOrderListItemResponse> dbRecords = orderReadReplicaQueryExecutor.query(() ->
                orderMapper.pageOrdersForAdmin(status, orderNo, 1, 0)
                        .stream()
                        .map(row -> listItem(row, SOURCE_DB))
                        .toList());
        List<AdminOrderListItemResponse> merged = merge(redisRecords, dbRecords);
        long offset = (long) (page - 1) * pageSize;
        List<AdminOrderListItemResponse> records = merged.stream()
                .skip(offset)
                .limit(pageSize)
                .toList();
        return new AdminOrderPageResponse(page, pageSize, merged.size(), records);
    }

    private long redisOnlyCount(List<AdminOrderListItemResponse> redisRecords,
                                List<AdminOrderListItemResponse> dbRecords) {
        Set<String> dbOrderNos = dbRecords.stream()
                .map(AdminOrderListItemResponse::orderNo)
                .collect(Collectors.toSet());
        return redisRecords.stream()
                .filter(record -> !dbOrderNos.contains(record.orderNo()))
                .count();
    }

    private List<AdminOrderListItemResponse> merge(List<AdminOrderListItemResponse> redisRecords,
                                                   List<AdminOrderListItemResponse> dbRecords) {
        Map<String, AdminOrderListItemResponse> unique = new LinkedHashMap<>();
        redisRecords.forEach(record -> unique.put(record.orderNo(), record));
        dbRecords.forEach(record -> unique.compute(
                record.orderNo(),
                (orderNo, existing) -> existing == null ? record : withSource(existing, SOURCE_MERGED)
        ));
        List<AdminOrderListItemResponse> merged = new ArrayList<>(unique.values());
        merged.sort(Comparator.comparing(
                AdminOrderListItemResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return merged;
    }

    private AdminOrderListItemResponse withSource(AdminOrderListItemResponse record, String source) {
        return new AdminOrderListItemResponse(
                record.orderNo(),
                record.userId(),
                record.status(),
                record.totalAmountYuan(),
                record.discountAmountYuan(),
                record.payAmountYuan(),
                record.userCouponId(),
                record.expireAt(),
                record.paidAt(),
                record.closingAt(),
                record.closingDeadlineAt(),
                record.cancelledAt(),
                record.closedAt(),
                record.createdAt(),
                record.updatedAt(),
                record.firstSkuName(),
                record.firstSkuImageUrl(),
                record.itemCount(),
                source
        );
    }

    private AdminOrderListItemResponse listItem(Map<String, Object> row, String source) {
        return new AdminOrderListItemResponse(
                OrderRowMapper.text(row, "orderNo"),
                OrderRowMapper.longValue(row, "userId"),
                OrderRowMapper.text(row, "status"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "totalAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "discountAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "payAmountYuan")),
                OrderRowMapper.idText(row, "userCouponId"),
                OrderRowMapper.offsetDateTime(row, "expireAt"),
                OrderRowMapper.offsetDateTime(row, "paidAt"),
                OrderRowMapper.offsetDateTime(row, "closingAt"),
                OrderRowMapper.offsetDateTime(row, "closingDeadlineAt"),
                OrderRowMapper.offsetDateTime(row, "cancelledAt"),
                OrderRowMapper.offsetDateTime(row, "closedAt"),
                OrderRowMapper.offsetDateTime(row, "createdAt"),
                OrderRowMapper.offsetDateTime(row, "updatedAt"),
                OrderRowMapper.text(row, "firstSkuName"),
                OrderRowMapper.text(row, "firstSkuImageUrl"),
                OrderRowMapper.intValue(row, "itemCount", 0),
                source
        );
    }

    private AdminOrderListItemResponse listItem(Map<String, Object> order,
                                                List<Map<String, Object>> items,
                                                String source) {
        Map<String, Object> firstItem = items == null || items.isEmpty() ? Map.of() : items.get(0);
        return new AdminOrderListItemResponse(
                OrderRowMapper.text(order, "orderNo"),
                OrderRowMapper.longValue(order, "userId"),
                OrderRowMapper.text(order, "status"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "totalAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "discountAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "payAmountYuan")),
                OrderRowMapper.idText(order, "userCouponId"),
                OrderRowMapper.offsetDateTime(order, "expireAt"),
                OrderRowMapper.offsetDateTime(order, "paidAt"),
                OrderRowMapper.offsetDateTime(order, "closingAt"),
                OrderRowMapper.offsetDateTime(order, "closingDeadlineAt"),
                OrderRowMapper.offsetDateTime(order, "cancelledAt"),
                OrderRowMapper.offsetDateTime(order, "closedAt"),
                OrderRowMapper.offsetDateTime(order, "createdAt"),
                OrderRowMapper.offsetDateTime(order, "updatedAt"),
                OrderRowMapper.text(firstItem, "skuName"),
                OrderRowMapper.text(firstItem, "skuImageUrl"),
                items == null ? 0 : items.size(),
                source
        );
    }

    private AdminOrderDetailResponse detail(Map<String, Object> order,
                                            List<Map<String, Object>> items,
                                            String source) {
        return new AdminOrderDetailResponse(
                OrderRowMapper.text(order, "orderNo"),
                OrderRowMapper.longValue(order, "userId"),
                OrderRowMapper.text(order, "status"),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "totalAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "discountAmountYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(order, "payAmountYuan")),
                nonNegativeLong(OrderRowMapper.longValue(order, "requiredPoints")),
                OrderRowMapper.idText(order, "userCouponId"),
                OrderRowMapper.offsetDateTime(order, "expireAt"),
                OrderRowMapper.offsetDateTime(order, "paidAt"),
                OrderRowMapper.offsetDateTime(order, "closingAt"),
                OrderRowMapper.offsetDateTime(order, "closingDeadlineAt"),
                OrderRowMapper.offsetDateTime(order, "cancelledAt"),
                OrderRowMapper.offsetDateTime(order, "closedAt"),
                OrderRowMapper.offsetDateTime(order, "createdAt"),
                OrderRowMapper.offsetDateTime(order, "updatedAt"),
                source,
                items == null ? List.of() : items.stream().map(this::item).toList()
        );
    }

    private AdminOrderItemResponse item(Map<String, Object> row) {
        return new AdminOrderItemResponse(
                OrderRowMapper.idText(row, "skuId"),
                OrderRowMapper.longValue(row, "spuId"),
                OrderRowMapper.text(row, "skuCode"),
                OrderRowMapper.text(row, "skuName"),
                OrderRowMapper.text(row, "specJson"),
                OrderRowMapper.text(row, "skuImageUrl"),
                OrderRowMapper.intValue(row, "quantity", 0),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "salePriceYuan")),
                OrderAmountCalculator.money(OrderRowMapper.decimal(row, "lineAmountYuan")),
                OrderRowMapper.boolValue(row, "hotSku"),
                OrderRowMapper.offsetDateTime(row, "createdAt")
        );
    }

    private long nonNegativeLong(Long value) {
        return value == null || value < 0L ? 0L : value;
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!SUPPORTED_STATUSES.contains(value)) {
            throw new AdminServiceException("ADMIN_ORDER_STATUS_INVALID", "订单状态无效。", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeOptionalOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty()) {
            return null;
        }
        return validateOrderNo(value);
    }

    private String normalizeRequiredOrderNo(String orderNo) {
        String value = orderNo == null ? "" : orderNo.trim();
        if (value.isEmpty()) {
            throw new AdminServiceException("ADMIN_ORDER_NO_INVALID", "订单号无效。", HttpStatus.BAD_REQUEST);
        }
        return validateOrderNo(value);
    }

    private String validateOrderNo(String orderNo) {
        if (orderNo.length() > 64 || !orderNo.chars().allMatch(this::isBase62Char)) {
            throw new AdminServiceException("ADMIN_ORDER_NO_INVALID", "订单号无效。", HttpStatus.BAD_REQUEST);
        }
        return orderNo;
    }

    private boolean isBase62Char(int ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private record AdminOrderDbPage(long total, List<AdminOrderListItemResponse> records) {
    }
}
