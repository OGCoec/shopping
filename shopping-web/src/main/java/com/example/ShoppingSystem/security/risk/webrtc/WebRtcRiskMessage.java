package com.example.ShoppingSystem.security.risk.webrtc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebRtcRiskMessage {

    @Builder.Default
    private int schemaVersion = 1;

    private String eventId;
    private WebRtcRiskScope scope;
    private String subjectRef;
    private String httpIp;
    private List<String> webRtcIps;
    private String webRtcStatus;
    private String deviceFingerprintHash;
    private String userAgentHash;
    private Long durationMillis;
    private String diagnosticReason;

    @Builder.Default
    private long observedAtEpochMillis = System.currentTimeMillis();

    @Builder.Default
    private int retryCount = 0;

    private String lastErrorMessage;

    public WebRtcRiskMessage nextRetry(String errorMessage) {
        return WebRtcRiskMessage.builder()
                .schemaVersion(schemaVersion)
                .eventId(eventId)
                .scope(scope)
                .subjectRef(subjectRef)
                .httpIp(httpIp)
                .webRtcIps(webRtcIps)
                .webRtcStatus(webRtcStatus)
                .deviceFingerprintHash(deviceFingerprintHash)
                .userAgentHash(userAgentHash)
                .durationMillis(durationMillis)
                .diagnosticReason(diagnosticReason)
                .observedAtEpochMillis(observedAtEpochMillis)
                .retryCount(retryCount + 1)
                .lastErrorMessage(errorMessage)
                .build();
    }

    public WebRtcRiskMessage markFailed(String errorMessage) {
        return WebRtcRiskMessage.builder()
                .schemaVersion(schemaVersion)
                .eventId(eventId)
                .scope(scope)
                .subjectRef(subjectRef)
                .httpIp(httpIp)
                .webRtcIps(webRtcIps)
                .webRtcStatus(webRtcStatus)
                .deviceFingerprintHash(deviceFingerprintHash)
                .userAgentHash(userAgentHash)
                .durationMillis(durationMillis)
                .diagnosticReason(diagnosticReason)
                .observedAtEpochMillis(observedAtEpochMillis)
                .retryCount(retryCount)
                .lastErrorMessage(errorMessage)
                .build();
    }
}
