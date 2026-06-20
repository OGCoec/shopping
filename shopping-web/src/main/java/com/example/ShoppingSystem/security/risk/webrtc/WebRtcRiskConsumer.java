package com.example.ShoppingSystem.security.risk.webrtc;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.outbox.InboxIdempotentConsumerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WebRtcRiskConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebRtcRiskConsumer.class);

    private static final String CONSUMER_NAME = "webrtc-risk-eval-risk";

    private final WebRtcRiskEvaluationService evaluationService;
    private final WebRtcRiskDispatcher dispatcher;
    private final WebRtcRiskRabbitProperties rabbitProperties;
    private final InboxIdempotentConsumerExecutor inboxExecutor;

    public WebRtcRiskConsumer(WebRtcRiskEvaluationService evaluationService,
                              WebRtcRiskDispatcher dispatcher,
                              WebRtcRiskRabbitProperties rabbitProperties,
                              InboxIdempotentConsumerExecutor inboxExecutor) {
        this.evaluationService = evaluationService;
        this.dispatcher = dispatcher;
        this.rabbitProperties = rabbitProperties;
        this.inboxExecutor = inboxExecutor;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.webrtc-risk.queue:risk.webrtc.check.queue}",
            containerFactory = "webRtcRiskRabbitListenerContainerFactory"
    )
    public void consume(WebRtcRiskMessage message) {
        if (message == null) {
            return;
        }
        try {
            // 写回目标库为 RISK，使用 RISK 库 inbox_event 做数据库级幂等
            inboxExecutor.execute(
                    DataSourceRoute.RISK,
                    message.getEventId(),
                    CONSUMER_NAME,
                    () -> evaluationService.evaluateAndWriteBack(message)
            );
        } catch (Exception e) {
            handleFailure(message, e);
        }
    }

    private void handleFailure(WebRtcRiskMessage message, Exception exception) {
        String errorMessage = exception.getMessage();
        if (message.getRetryCount() < rabbitProperties.getMaxRetryCount()) {
            WebRtcRiskMessage retryMessage = message.nextRetry(errorMessage);
            long delayMillis = retryDelayMillis(message.getRetryCount());
            dispatcher.publishRetry(retryMessage, delayMillis);
            log.warn("WebRTC risk message retry scheduled, eventId={}, retryCount={}, delayMillis={}, error={}",
                    message.getEventId(), retryMessage.getRetryCount(), delayMillis, errorMessage);
            return;
        }
        WebRtcRiskMessage deadLetterMessage = message.markFailed(errorMessage);
        dispatcher.publishDeadLetter(deadLetterMessage);
        log.error("WebRTC risk message moved to dead letter, eventId={}, retryCount={}, error={}",
                message.getEventId(), message.getRetryCount(), errorMessage);
    }

    private long retryDelayMillis(int retryCount) {
        return switch (retryCount) {
            case 0 -> 10_000L;
            case 1 -> 30_000L;
            case 2 -> 120_000L;
            default -> 300_000L;
        };
    }
}