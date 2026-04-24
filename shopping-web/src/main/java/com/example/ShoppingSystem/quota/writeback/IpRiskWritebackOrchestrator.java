package com.example.ShoppingSystem.quota.writeback;

import com.example.ShoppingSystem.quota.IpRiskCachedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates sync/async IP risk writeback execution according to source-based policy.
 */
@Service
public class IpRiskWritebackOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IpRiskWritebackOrchestrator.class);

    private final IpRiskWritebackProperties properties;
    private final IpRiskWritebackPlanResolver planResolver;
    private final IpRiskWritebackDispatcher dispatcher;
    private final IpRiskWritebackExecutorService executorService;

    public IpRiskWritebackOrchestrator(IpRiskWritebackProperties properties,
                                       IpRiskWritebackPlanResolver planResolver,
                                       IpRiskWritebackDispatcher dispatcher,
                                       IpRiskWritebackExecutorService executorService) {
        this.properties = properties;
        this.planResolver = planResolver;
        this.dispatcher = dispatcher;
        this.executorService = executorService;
    }

    public void orchestrate(String source, String ip, IpRiskCachedPayload payload) {
        if (!properties.isEnabled() || ip == null || ip.isBlank() || payload == null) {
            return;
        }

        Set<IpRiskWritebackAction> actions = planResolver.resolveActions(source);
        if (actions == null || actions.isEmpty()) {
            return;
        }

        IpRiskWritebackMode mode = planResolver.resolveMode();
        switch (mode) {
            case SYNC -> executorService.executeActions(ip, payload, actions);
            case HYBRID -> executeHybrid(source, ip, payload, actions);
            case ASYNC -> dispatchAsyncWithFallback(source, ip, payload, actions);
        }
    }

    private void executeHybrid(String source, String ip, IpRiskCachedPayload payload, Set<IpRiskWritebackAction> actions) {
        EnumSet<IpRiskWritebackAction> syncActions = EnumSet.noneOf(IpRiskWritebackAction.class);
        EnumSet<IpRiskWritebackAction> asyncActions = EnumSet.copyOf(actions);
        if (asyncActions.contains(IpRiskWritebackAction.WARM_LOCAL_CACHE)) {
            syncActions.add(IpRiskWritebackAction.WARM_LOCAL_CACHE);
            asyncActions.remove(IpRiskWritebackAction.WARM_LOCAL_CACHE);
        }
        if (!syncActions.isEmpty()) {
            executorService.executeActions(ip, payload, syncActions);
        }
        if (!asyncActions.isEmpty()) {
            dispatchAsyncWithFallback(source, ip, payload, asyncActions);
        }
    }

    private void dispatchAsyncWithFallback(String source, String ip, IpRiskCachedPayload payload, Set<IpRiskWritebackAction> actions) {
        IpRiskWritebackCommand command = IpRiskWritebackCommand.builder()
                .eventId(nextEventId())
                .publicIp(ip)
                .source(source)
                .actions(EnumSet.copyOf(actions))
                .payload(payload)
                .createdAtEpochMillis(System.currentTimeMillis())
                .build();
        try {
            dispatcher.dispatch(command);
        } catch (Exception ex) {
            log.warn("IP风险异步回写消息发布失败：eventId={}，source={}，ip={}，reason={}",
                    command.getEventId(), source, ip, ex.getMessage());
            if (properties.isFallbackSyncOnPublishFailure()) {
                executorService.executeActions(ip, payload, actions);
            }
        }
    }

    private String nextEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
