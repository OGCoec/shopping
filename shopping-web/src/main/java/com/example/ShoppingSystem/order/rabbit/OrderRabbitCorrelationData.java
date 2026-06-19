package com.example.ShoppingSystem.order.rabbit;

import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.util.UUID;

public class OrderRabbitCorrelationData extends CorrelationData {

    public static final String PHASE_CLOSING_FINALIZE = "closing-finalize";
    public static final String HEADER_PHASE = "order-publish-phase";
    public static final String HEADER_ORDER_NO = "order-no";
    public static final String HEADER_CORRELATION_ID = "order-publish-correlation-id";
    public static final String HEADER_CLOSING_DEADLINE_EPOCH_MILLI = "closing-deadline-epoch-milli";
    public static final String HEADER_DELAY_MILLIS = "delay-millis";

    private final String phase;
    private final String orderNo;
    private final String exchange;
    private final String routingKey;
    private final long delayMillis;
    private final Long closingDeadlineEpochMilli;
    private final long createdAtEpochMilli;

    private OrderRabbitCorrelationData(String id,
                                       String phase,
                                       String orderNo,
                                       String exchange,
                                       String routingKey,
                                       long delayMillis,
                                       Long closingDeadlineEpochMilli,
                                       long createdAtEpochMilli) {
        super(id);
        this.phase = phase;
        this.orderNo = orderNo;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.delayMillis = delayMillis;
        this.closingDeadlineEpochMilli = closingDeadlineEpochMilli;
        this.createdAtEpochMilli = createdAtEpochMilli;
    }

    public static OrderRabbitCorrelationData closingFinalize(String orderNo,
                                                            String exchange,
                                                            String routingKey,
                                                            long delayMillis,
                                                            Long closingDeadlineEpochMilli) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
        long normalizedDelayMillis = Math.max(1L, delayMillis);
        String id = PHASE_CLOSING_FINALIZE + ":" + normalizedOrderNo + ":" + normalizedDelayMillis + ":" + UUID.randomUUID();
        return new OrderRabbitCorrelationData(
                id,
                PHASE_CLOSING_FINALIZE,
                normalizedOrderNo,
                exchange,
                routingKey,
                normalizedDelayMillis,
                closingDeadlineEpochMilli,
                System.currentTimeMillis()
        );
    }

    public String phase() {
        return phase;
    }

    public String orderNo() {
        return orderNo;
    }

    public String exchange() {
        return exchange;
    }

    public String routingKey() {
        return routingKey;
    }

    public long delayMillis() {
        return delayMillis;
    }

    public Long closingDeadlineEpochMilli() {
        return closingDeadlineEpochMilli;
    }

    public long createdAtEpochMilli() {
        return createdAtEpochMilli;
    }
}
