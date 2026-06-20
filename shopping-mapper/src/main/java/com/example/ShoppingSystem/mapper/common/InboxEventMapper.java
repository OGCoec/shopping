package com.example.ShoppingSystem.mapper.common;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

@Mapper
public interface InboxEventMapper {

    int insertProcessing(@Param("eventId") String eventId,
                         @Param("consumerName") String consumerName,
                         @Param("receivedAt") OffsetDateTime receivedAt);

    int tryStartProcessing(@Param("eventId") String eventId,
                           @Param("consumerName") String consumerName,
                           @Param("processingTimeoutMs") long processingTimeoutMs,
                           @Param("now") OffsetDateTime now);

    int markProcessed(@Param("eventId") String eventId,
                      @Param("consumerName") String consumerName,
                      @Param("processedAt") OffsetDateTime processedAt);

    int markFailed(@Param("eventId") String eventId,
                   @Param("consumerName") String consumerName,
                   @Param("lastError") String lastError);
}
