package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.order.dto.OrderCreateResponse;
import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scripting.support.ResourceScriptSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface OrderRedisSnapshotService {
    public void saveCreatedOrder(OrderCreateContext context,
                                 LockedOrderCoupon lockedCoupon,
                                 BigDecimal totalAmount,
                                 BigDecimal discountAmount,
                                 BigDecimal payAmount,
                                 long requiredPoints);

    public Optional<OrderRedisSnapshot> findSnapshot(String orderNo);

    public Optional<OrderRedisSnapshot> findSnapshotForUser(String orderNo, Long userId);

    public Optional<OrderCreateResponse> findCreateResponse(Long userId, String orderNo);

    public List<OrderRedisSnapshot> listUserSnapshots(Long userId, int limit);

    public List<OrderRedisSnapshot> listAllSnapshots(int limit);

    public OrderRedisStateChangeResult cancelPending(Long userId, String orderNo, OffsetDateTime now);

    public OrderRedisStateChangeResult startClosingExpired(String orderNo,
                                                           OffsetDateTime now,
                                                           OffsetDateTime closingDeadline);

    public OrderRedisStateChangeResult finalizeClosing(String orderNo, OffsetDateTime now);

    public OrderClosingCompensateBatchResult compensateDueClosing(OffsetDateTime now, int batchSize);

    public OrderRedisStateChangeResult markPaid(String orderNo,
                                                OffsetDateTime paidAt,
                                                String externalTradeNo);

    public OrderRedisStateChangeResult markPendingPaidForUser(String orderNo,
                                                              Long userId,
                                                              OffsetDateTime paidAt,
                                                              String externalTradeNo);

    public List<Map<String, Object>> markPaidBatch(List<PaymentCallbackEvent> callbacks);

    public List<String> claimDirty(int batchSize, long nowEpochMs);

    public List<String> recoverTimedOutProcessing(Duration timeout, int batchSize, long nowEpochMs);

    public boolean acquirePersistLock(String lockValue, Duration ttl);

    public void releasePersistLock(String lockValue);

    public boolean acquireClosingCompensateLock(String lockValue, Duration ttl);

    public void releaseClosingCompensateLock(String lockValue);

    public List<OrderRedisSnapshot> loadSnapshots(List<String> orderNos);

    public void completePersistedAndCleanup(Collection<String> orderNos, List<OrderRedisSnapshot> snapshots);

    public void requeueProcessing(Collection<String> orderNos,
                                  List<OrderRedisSnapshot> snapshots,
                                  long fallbackEpochMs);
}
