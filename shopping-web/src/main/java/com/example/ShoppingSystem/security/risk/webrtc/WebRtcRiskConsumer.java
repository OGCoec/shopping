package com.example.ShoppingSystem.security.risk.webrtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WebRtcRiskConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebRtcRiskConsumer.class);

    private final WebRtcRiskEvaluationService evaluationService;
    private final WebRtcRiskDispatcher dispatcher;
    private final WebRtcRiskRabbitProperties rabbitProperties;
    private final WebRtcRiskIdempotencyService idempotencyService;

    public WebRtcRiskConsumer(WebRtcRiskEvaluationService evaluationService,
                              WebRtcRiskDispatcher dispatcher,
                              WebRtcRiskRabbitProperties rabbitProperties,
                              WebRtcRiskIdempotencyService idempotencyService) {
        this.evaluationService = evaluationService;
        this.dispatcher = dispatcher;
        this.rabbitProperties = rabbitProperties;
        this.idempotencyService = idempotencyService;
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
            if (!idempotencyService.markProcessing(message.getEventId())) {
                return;
            }
            evaluationService.evaluateAndWriteBack(message);
        } catch (Exception e) {
            idempotencyService.clearProcessing(message.getEventId());
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
