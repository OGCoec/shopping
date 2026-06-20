package com.example.ShoppingSystem.order.service;

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
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface PaymentCallbackStreamService {
    public record EnqueueResult(String callbackNo,
                                    boolean created,
                                    String streamMessageId) {
        }

    public void initGroup();

    public EnqueueResult enqueue(String callbackNo,
                                 String orderNo,
                                 String externalTradeNo,
                                 String paymentProvider,
                                 OffsetDateTime paidAt,
                                 BigDecimal paidAmountYuan,
                                 String idempotencyKey,
                                 String rawPayloadJson,
                                 OffsetDateTime receivedAt);

    public List<PaymentCallbackStreamRecord> readBatch();

    public long ackAndDelete(List<String> streamMessageIds);
}
