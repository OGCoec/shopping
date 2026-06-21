package com.example.ShoppingSystem.mapper.common;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OutboxEventMapper {

    int insertEvent(@Param("eventId") String eventId,
                    @Param("eventType") String eventType,
                    @Param("aggregateType") String aggregateType,
                    @Param("aggregateId") String aggregateId,
                    @Param("exchangeName") String exchangeName,
                    @Param("routingKey") String routingKey,
                    @Param("payloadJson") String payloadJson,
                    @Param("idempotencyKey") String idempotencyKey,
                    @Param("createdAt") OffsetDateTime createdAt);

    int insertEvents(@Param("events") List<OutboxEventRow> events);

    List<Map<String, Object>> claimBatch(@Param("limit") int limit,
                                         @Param("maxRetry") int maxRetry,
                                         @Param("processingTimeoutMs") long processingTimeoutMs);

    int markPublished(@Param("eventId") String eventId,
                      @Param("publishedAt") OffsetDateTime publishedAt);

    int markRetry(@Param("eventId") String eventId,
                  @Param("maxRetry") int maxRetry,
                  @Param("nextRetryAt") OffsetDateTime nextRetryAt,
                  @Param("lastError") String lastError);
}
