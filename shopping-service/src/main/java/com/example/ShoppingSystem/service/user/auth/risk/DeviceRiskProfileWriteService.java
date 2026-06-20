package com.example.ShoppingSystem.service.user.auth.risk;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.example.ShoppingSystem.common.transaction.AfterCommitExecutor;
import com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper;
import com.example.ShoppingSystem.mapper.risk.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.service.user.auth.register.impl.ChallengePolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface DeviceRiskProfileWriteService {
    public void recordSuccess(Long userId, String deviceFingerprint, String clientIp, String scene);

    public void recordFailure(Long userId, String deviceFingerprint, String clientIp, String scene);

    public int ensureProfileExists(String deviceFingerprint, String clientIp);

    public void applyAutomationPenalty(String deviceFingerprint, String clientIp, int penaltyScore, String reason);
}
