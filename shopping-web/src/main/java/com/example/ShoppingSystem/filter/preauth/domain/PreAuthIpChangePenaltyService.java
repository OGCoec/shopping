package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import com.example.ShoppingSystem.mapper.risk.RegisterRiskProfileMapper;
import com.example.ShoppingSystem.quota.IpCountryQueryService;
import com.example.ShoppingSystem.quota.IpGeoSnapshot;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceL6CountingBloomDecisionService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceRiskCacheInvalidator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public interface PreAuthIpChangePenaltyService {
    public PreAuthBinding applyShortTermPenalty(PreAuthBinding existing,
                                                String currentIp,
                                                String normalizedFingerprint);
}
