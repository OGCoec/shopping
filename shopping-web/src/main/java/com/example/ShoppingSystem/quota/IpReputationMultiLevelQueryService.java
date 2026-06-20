package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper;
import com.example.ShoppingSystem.quota.writeback.IpRiskWritebackOrchestrator;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

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
