package com.example.ShoppingSystem.order.service.impl.PaymentCallbackStreamService;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.order.service.PaymentCallbackStreamService;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import com.example.ShoppingSystem.order.service.PaymentCallbackStreamProperties;
import com.example.ShoppingSystem.order.service.PaymentCallbackStreamRecord;
@Service
public class PaymentCallbackStreamServiceImpl implements PaymentCallbackStreamService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackStreamService.class);

    private static final String DEDUPE_PREFIX = "shopping:payment:callback:dedupe:";

    private final StringRedisTemplate stringRedisTemplate;
    private final PaymentCallbackStreamProperties properties;
    private final DefaultRedisScript<List> enqueueScript;
    private final DefaultRedisScript<Long> ackDeleteScript;
    private final String consumerName;

    public PaymentCallbackStreamServiceImpl(StringRedisTemplate stringRedisTemplate,
                                        PaymentCallbackStreamProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.enqueueScript = listRedisScript("lua/payment_callback_stream_enqueue.lua");
        this.ackDeleteScript = longRedisScript("lua/payment_stream_ack_delete_batch.lua");
        this.consumerName = resolveConsumerName(properties.getConsumerName());
    }

    @PostConstruct
    public void initGroup() {
        if (!properties.isEnabled()) {
            return;
        }
        ensureGroup();
    }

    public EnqueueResult enqueue(String callbackNo,
                                 String orderNo,
                                 String externalTradeNo,
                                 String paymentProvider,
                                 OffsetDateTime paidAt,
                                 BigDecimal paidAmountYuan,
                                 String idempotencyKey,
                                 String rawPayloadJson,
                                 OffsetDateTime receivedAt) {
        if (!properties.isEnabled()) {
            throw new OrderServiceException(
                    "ORDER_PAYMENT_CALLBACK_STREAM_DISABLED",
                    "Payment callback stream is disabled.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        OffsetDateTime normalizedPaidAt = paidAt == null ? OffsetDateTime.now() : paidAt;
        OffsetDateTime normalizedReceivedAt = receivedAt == null ? OffsetDateTime.now() : receivedAt;
        List<?> result = stringRedisTemplate.execute(
                enqueueScript,
                List.of(properties.getKey(), DEDUPE_PREFIX + idempotencyKey),
                callbackNo,
                orderNo,
                blankToEmpty(externalTradeNo),
                paymentProvider,
                String.valueOf(normalizedPaidAt.toInstant().toEpochMilli()),
                paidAmountYuan == null ? "" : paidAmountYuan.toPlainString(),
                idempotencyKey,
                rawPayloadJson,
                String.valueOf(normalizedReceivedAt.toInstant().toEpochMilli()),
                String.valueOf(Math.max(1L, properties.getDedupeTtlHours()) * 3600L)
        );
        if (result == null || result.isEmpty()) {
            throw new OrderServiceException(
                    "ORDER_PAYMENT_CALLBACK_STREAM_WRITE_FAILED",
                    "Payment callback stream write failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        String resolvedCallbackNo = text(result.get(0));
        boolean created = result.size() > 1 && "1".equals(text(result.get(1)));
        String streamMessageId = result.size() > 2 ? text(result.get(2)) : "";
        return new EnqueueResult(resolvedCallbackNo, created, streamMessageId);
    }

    public List<PaymentCallbackStreamRecord> readBatch() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        ensureGroup();
        int limit = normalizeBatchSize(properties.getBatchSize());
        Map<String, PaymentCallbackStreamRecord> recordsById = new LinkedHashMap<>();
        for (PaymentCallbackStreamRecord record : autoClaim(limit)) {
            recordsById.putIfAbsent(record.streamMessageId(), record);
        }
        int remaining = limit - recordsById.size();
        if (remaining > 0) {
            for (PaymentCallbackStreamRecord record : readNew(remaining)) {
                recordsById.putIfAbsent(record.streamMessageId(), record);
            }
        }
        return new ArrayList<>(recordsById.values());
    }

    public long ackAndDelete(List<String> streamMessageIds) {
        if (streamMessageIds == null || streamMessageIds.isEmpty()) {
            return 0L;
        }
        List<String> args = new ArrayList<>(streamMessageIds.size() + 1);
        args.add(properties.getGroup());
        streamMessageIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .forEach(args::add);
        if (args.size() == 1) {
            return 0L;
        }
        Long count = stringRedisTemplate.execute(ackDeleteScript, List.of(properties.getKey()), (Object[]) args.toArray(String[]::new));
        return count == null ? 0L : count;
    }

    private List<PaymentCallbackStreamRecord> readNew(int limit) {
        List<ByteRecord> records = stringRedisTemplate.execute((RedisCallback<List<ByteRecord>>) connection ->
                connection.xReadGroup(
                        Consumer.from(properties.getGroup(), consumerName),
                        StreamReadOptions.empty().count(Math.max(1, limit)),
                        StreamOffset.create(bytes(properties.getKey()), ReadOffset.lastConsumed())
                )
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(this::toRecord)
                .toList();
    }

    private List<PaymentCallbackStreamRecord> autoClaim(int limit) {
        List<ByteRecord> records = stringRedisTemplate.execute((RedisCallback<List<ByteRecord>>) connection -> {
            PendingMessages pending = connection.xPending(
                    bytes(properties.getKey()),
                    properties.getGroup(),
                    Range.unbounded(),
                    (long) Math.max(1, limit)
            );
            if (pending == null || pending.isEmpty()) {
                return List.of();
            }
            List<RecordId> ids = new ArrayList<>(pending.size());
            for (PendingMessage message : pending) {
                ids.add(message.getId());
            }
            if (ids.isEmpty()) {
                return List.of();
            }
            return connection.xClaim(
                    bytes(properties.getKey()),
                    properties.getGroup(),
                    consumerName,
                    Duration.ofMillis(Math.max(1L, properties.getPendingIdleTimeoutMs())),
                    ids.toArray(RecordId[]::new)
            );
        });
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(this::toRecord)
                .toList();
    }

    private PaymentCallbackStreamRecord toRecord(ByteRecord record) {
        Map<String, String> body = new LinkedHashMap<>(record.getValue().size());
        for (Map.Entry<byte[], byte[]> entry : record.getValue().entrySet()) {
            body.put(text(entry.getKey()), text(entry.getValue()));
        }
        return new PaymentCallbackStreamRecord(record.getId().getValue(), body);
    }

    private void ensureGroup() {
        try {
            stringRedisTemplate.execute((RedisCallback<String>) connection ->
                    connection.xGroupCreate(bytes(properties.getKey()), properties.getGroup(), ReadOffset.from("0-0"), true)
            );
        } catch (RedisSystemException e) {
            if (!isBusyGroup(e)) {
                throw e;
            }
        }
    }

    private boolean isBusyGroup(Exception e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("BUSYGROUP")
                    || message.contains("Consumer Group name already exists"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveConsumerName(String configured) {
        String value = configured == null ? "" : configured.trim();
        if (!value.isBlank()) {
            return value;
        }
        return hostName() + "-" + ManagementFactory.getRuntimeMXBean().getName().replace('@', '-');
    }

    private String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.warn("[PaymentCallbackStream] hostname resolve failed", e);
            return "instance";
        }
    }

    private int normalizeBatchSize(int batchSize) {
        return Math.max(1, Math.min(batchSize, 500));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private byte[] bytes(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private DefaultRedisScript<List> listRedisScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(List.class);
        return script;
    }

    private DefaultRedisScript<Long> longRedisScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(Long.class);
        return script;
    }
}
