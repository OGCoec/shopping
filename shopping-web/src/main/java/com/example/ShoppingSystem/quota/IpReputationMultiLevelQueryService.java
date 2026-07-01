package com.example.ShoppingSystem.quota;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
public interface IpReputationMultiLevelQueryService {
    public record MultiLevelQueryResult(boolean success,
                                            String source,
                                            String reason,
                                            IpReputationEvidence evidence,
                                            Integer currentScore) {
            public static MultiLevelQueryResult success(String source, IpReputationEvidence evidence, Integer currentScore) {
                return new MultiLevelQueryResult(true, source, "ok", evidence, currentScore);
            }

            public static MultiLevelQueryResult failed(String source, String reason) {
                return new MultiLevelQueryResult(false, source, reason, null, null);
            }
        }

    public MultiLevelQueryResult queryEvidence(String publicIp);
}
