package com.example.ShoppingSystem.security.risk;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.common.transaction.AfterCommitExecutor;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.filter.preauth.PreAuthHeaders;
import com.example.ShoppingSystem.filter.preauth.domain.TrustedExitIpMatcher;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthIpNormalizer;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthRequestResolver;
import com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper;
import com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper;
import com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import com.example.ShoppingSystem.quota.IpGeoSnapshot;
import com.example.ShoppingSystem.quota.IpReputationMultiLevelQueryService;
import com.example.ShoppingSystem.redisdata.UserAuthRiskRedisKeys;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskDecision;
import com.example.ShoppingSystem.security.risk.webrtc.WebRtcRiskStatus;
import com.example.ShoppingSystem.security.token.AuthTokenService;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
import com.example.ShoppingSystem.service.user.auth.risk.TerminatedAccountEmailBloomService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface AccountNetworkRiskService {
    public record AccountNetworkRiskDecision(boolean allowed,
                                                 boolean terminationRequired,
                                                 Long retryAfterMs,
                                                 String status,
                                                 String reason,
                                                 String message) {

            public static AccountNetworkRiskDecision allow() {
                return new AccountNetworkRiskDecision(true, false, null, "", "", "");
            }

            public static AccountNetworkRiskDecision blocked(boolean terminationRequired,
                                                             Long retryAfterMs,
                                                             String status,
                                                             String reason,
                                                             String message) {
                return new AccountNetworkRiskDecision(false, terminationRequired, retryAfterMs, status, reason, message);
            }
        }

    public AccountNetworkRiskDecision evaluate(HttpServletRequest request);

    public void recordAsyncWebRtcRisk(Long userId,
                                      String currentIp,
                                      String deviceFingerprintHash,
                                      WebRtcRiskDecision decision,
                                      long observedAtEpochMillis);
}
