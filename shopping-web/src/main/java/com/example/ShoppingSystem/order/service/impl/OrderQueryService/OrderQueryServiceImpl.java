package com.example.ShoppingSystem.order.service.impl.OrderQueryService;

import com.example.ShoppingSystem.config.datasource.OrderReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.order.OrderMapper;
import com.example.ShoppingSystem.order.dto.OrderDetailResponse;
import com.example.ShoppingSystem.order.dto.OrderItemResponse;
import com.example.ShoppingSystem.order.dto.OrderPageItemResponse;
import com.example.ShoppingSystem.order.dto.OrderPageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.order.service.OrderQueryService;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshot;
import com.example.ShoppingSystem.order.service.OrderRedisSnapshotService;
import com.example.ShoppingSystem.order.service.OrderResponseAssembler;
import com.example.ShoppingSystem.order.service.OrderRowMapper;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.OrderStatus;
@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final OrderMapper orderMapper;
    private final OrderRedisSnapshotService orderRedisSnapshotService;
    private final OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor;

    public OrderQueryServiceImpl(OrderMapper orderMapper,
                             OrderRedisSnapshotService orderRedisSnapshotService,
                             OrderReadReplicaQueryExecutor orderReadReplicaQueryExecutor) {
        this.orderMapper = orderMapper;
        this.orderRedisSnapshotService = orderRedisSnapshotService;
        this.orderReadReplicaQueryExecutor = orderReadReplicaQueryExecutor;
    }

    public OrderDetailResponse detail(Long userId, String orderNo) {
        OrderRedisSnapshot snapshot = orderRedisSnapshotService.findSnapshotForUser(orderNo, userId).orElse(null);
        if (snapshot != null) {
            OrderDetailResponse terminalDetail = terminalDetailWhenRedisClosing(userId, orderNo, snapshot);
            if (terminalDetail != null) {
                return terminalDetail;
            }
            List<OrderItemResponse> items = snapshot.items()
                    .stream()
                    .map(OrderResponseAssembler::item)
                    .toList();
            return OrderResponseAssembler.detail(snapshot.order(), items);
        }
        return orderReadReplicaQueryExecutor.queryPrimary(() -> {
            Map<String, Object> order = orderMapper.findOrderByOrderNoForUser(orderNo, userId);
            if (order == null || order.isEmpty()) {
                throw new OrderServiceException("ORDER_NOT_FOUND", "Order does not exist.", HttpStatus.NOT_FOUND);
            }
            List<OrderItemResponse> items = orderMapper.listOrderItems(orderNo)
                    .stream()
                    .map(OrderResponseAssembler::item)
                    .toList();
            return OrderResponseAssembler.detail(order, items);
        });
    }

    private OrderDetailResponse terminalDetailWhenRedisClosing(Long userId, String orderNo, OrderRedisSnapshot snapshot) {
        if (!OrderStatus.CLOSING.equals(OrderRowMapper.text(snapshot.order(), "status"))) {
            return null;
        }
        return orderReadReplicaQueryExecutor.queryPrimary(() -> {
            Map<String, Object> order = orderMapper.findOrderByOrderNoForUser(orderNo, userId);
            if (order == null || order.isEmpty() || !isTerminal(OrderRowMapper.text(order, "status"))) {
                return null;
            }
            cleanupRedisTerminalSnapshot(orderNo, order);
            List<OrderItemResponse> items = orderMapper.listOrderItems(orderNo)
                    .stream()
                    .map(OrderResponseAssembler::item)
                    .toList();
            return OrderResponseAssembler.detail(order, items);
        });
    }

    public OrderPageResponse page(Long userId, Integer rawPage, Integer rawPageSize, String status) {
        int page = rawPage == null || rawPage <= 0 ? DEFAULT_PAGE : rawPage;
        int pageSize = rawPageSize == null || rawPageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(rawPageSize, MAX_PAGE_SIZE);
        String normalizedStatus = normalizeStatus(status);
        long offset = (long) (page - 1) * pageSize;
        int candidateLimit = Math.max(page * pageSize, pageSize);
        List<OrderRedisSnapshot> redisSnapshots = orderRedisSnapshotService.listUserSnapshots(userId, candidateLimit)
                .stream()
                .filter(snapshot -> normalizedStatus == null || normalizedStatus.equals(OrderRowMapper.text(snapshot.order(), "status")))
                .toList();
        OrderDbPage dbPage = orderReadReplicaQueryExecutor.query(() -> new OrderDbPage(
                orderMapper.countOrdersByUser(userId, normalizedStatus),
                orderMapper.pageOrdersByUser(userId, normalizedStatus, candidateLimit, 0)
                        .stream()
                        .map(OrderResponseAssembler::pageItem)
                        .toList()
        ));
        List<OrderPageItemResponse> redisRecords = redisSnapshots.stream()
                .map(snapshot -> OrderResponseAssembler.pageItem(snapshot.order(), snapshot.items()))
                .toList();
        List<OrderPageItemResponse> merged = mergePageItems(redisRecords, dbPage.records());
        List<OrderPageItemResponse> records = merged.stream()
                .skip(offset)
                .limit(pageSize)
                .toList();
        long adjustedTotal = Math.max(dbPage.total(), merged.size());
        return new OrderPageResponse(page, pageSize, adjustedTotal, records);
    }

    private List<OrderPageItemResponse> mergePageItems(List<OrderPageItemResponse> redisRecords,
                                                       List<OrderPageItemResponse> dbRecords) {
        Map<String, OrderPageItemResponse> unique = new LinkedHashMap<>();
        redisRecords.forEach(record -> unique.put(record.orderNo(), record));
        dbRecords.forEach(record -> unique.putIfAbsent(record.orderNo(), record));
        List<OrderPageItemResponse> merged = new ArrayList<>(unique.values());
        merged.sort(Comparator.comparing(
                OrderPageItemResponse::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return merged;
    }

    private boolean isTerminal(String status) {
        return OrderStatus.PAID.equals(status)
                || OrderStatus.CLOSED.equals(status)
                || OrderStatus.CANCELLED.equals(status);
    }

    private void cleanupRedisTerminalSnapshot(String orderNo, Map<String, Object> order) {
        try {
            orderRedisSnapshotService.completePersistedAndCleanup(
                    List.of(orderNo),
                    List.of(new OrderRedisSnapshot(order, List.of()))
            );
        } catch (Exception e) {
            log.warn("[Order] stale closing Redis snapshot cleanup failed, orderNo={}", orderNo, e);
        }
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!OrderStatus.PENDING_PAYMENT.equals(value)
                && !OrderStatus.CLOSING.equals(value)
                && !OrderStatus.PAID.equals(value)
                && !OrderStatus.CANCELLED.equals(value)
                && !OrderStatus.CLOSED.equals(value)) {
            throw new OrderServiceException("ORDER_STATUS_INVALID", "Order status is invalid.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private record OrderDbPage(long total, List<OrderPageItemResponse> records) {
    }
}
