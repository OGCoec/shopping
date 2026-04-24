package com.example.ShoppingSystem.quota.writeback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * RabbitMQ consumer for asynchronous IP risk writeback commands.
 */
@Component
public class IpRiskWritebackConsumer {

    private static final Logger log = LoggerFactory.getLogger(IpRiskWritebackConsumer.class);

    private final IpRiskWritebackExecutorService executorService;
    private final IpRiskWritebackDispatcher dispatcher;
    private final IpRiskWritebackRabbitProperties rabbitProperties;
    private final IpRiskWritebackIdempotencyService idempotencyService;

    public IpRiskWritebackConsumer(IpRiskWritebackExecutorService executorService,
                                   IpRiskWritebackDispatcher dispatcher,
                                   IpRiskWritebackRabbitProperties rabbitProperties,
                                   IpRiskWritebackIdempotencyService idempotencyService) {
        this.executorService = executorService;
        this.dispatcher = dispatcher;
        this.rabbitProperties = rabbitProperties;
        this.idempotencyService = idempotencyService;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.ip-risk-writeback.queue:ip.risk.writeback.queue}",
            containerFactory = "ipRiskWritebackRabbitListenerContainerFactory"
    )
    public void consume(IpRiskWritebackCommand command) {
        if (command == null) {
            return;
        }
        try {
            if (!idempotencyService.markProcessing(command.getEventId())) {
                return;
            }
            Set<IpRiskWritebackAction> actions = command.getActions();
            executorService.executeActions(command.getPublicIp(), command.getPayload(), actions);
        } catch (Exception e) {
            idempotencyService.clearProcessing(command.getEventId());
            handleFailure(command, e);
        }
    }

    void handleFailure(IpRiskWritebackCommand command, Exception exception) {
        String errorMessage = exception.getMessage();
        if (command.getRetryCount() < rabbitProperties.getMaxRetryCount()) {
            long delayMilli = resolveRetryDelayMilli(command.getRetryCount());
            IpRiskWritebackCommand retryCommand = command.nextRetry(errorMessage);
            dispatcher.publishRetry(retryCommand, delayMilli);
            log.warn("IP风险回写消息重试已投递：eventId={}，retryCount={}，delayMilli={}，error={}",
                    command.getEventId(), retryCommand.getRetryCount(), delayMilli, errorMessage);
            return;
        }

        IpRiskWritebackCommand deadLetterCommand = command.markFailed(errorMessage);
        dispatcher.publishDeadLetter(deadLetterCommand);
        log.error("IP风险回写消息进入死信队列：eventId={}，retryCount={}，error={}",
                command.getEventId(), command.getRetryCount(), errorMessage);
    }

    long resolveRetryDelayMilli(int currentRetryCount) {
        return switch (currentRetryCount) {
            case 0 -> 10_000L;
            case 1 -> 30_000L;
            case 2 -> 120_000L;
            default -> 300_000L;
        };
    }
}
