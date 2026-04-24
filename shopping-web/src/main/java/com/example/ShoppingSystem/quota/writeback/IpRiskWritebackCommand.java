package com.example.ShoppingSystem.quota.writeback;

import com.example.ShoppingSystem.quota.IpRiskCachedPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * MQ command that carries writeback intent and payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpRiskWritebackCommand {

    @Builder.Default
    private int schemaVersion = 1;

    private String eventId;
    private String publicIp;
    private String source;
    private Set<IpRiskWritebackAction> actions;
    private IpRiskCachedPayload payload;

    @Builder.Default
    private int retryCount = 0;

    @Builder.Default
    private long createdAtEpochMillis = System.currentTimeMillis();

    private String lastErrorMessage;

    public IpRiskWritebackCommand nextRetry(String errorMessage) {
        return IpRiskWritebackCommand.builder()
                .schemaVersion(schemaVersion)
                .eventId(eventId)
                .publicIp(publicIp)
                .source(source)
                .actions(actions)
                .payload(payload)
                .retryCount(retryCount + 1)
                .createdAtEpochMillis(createdAtEpochMillis)
                .lastErrorMessage(errorMessage)
                .build();
    }

    public IpRiskWritebackCommand markFailed(String errorMessage) {
        return IpRiskWritebackCommand.builder()
                .schemaVersion(schemaVersion)
                .eventId(eventId)
                .publicIp(publicIp)
                .source(source)
                .actions(actions)
                .payload(payload)
                .retryCount(retryCount)
                .createdAtEpochMillis(createdAtEpochMillis)
                .lastErrorMessage(errorMessage)
                .build();
    }
}
