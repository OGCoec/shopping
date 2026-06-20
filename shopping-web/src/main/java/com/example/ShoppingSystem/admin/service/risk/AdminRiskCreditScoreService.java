package com.example.ShoppingSystem.admin.service.risk;

import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceScoreEventResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskCountryResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.ShoppingSystem.config.datasource.RiskReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.risk.AdminDeviceRiskProfileMapper;
import com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper;
import com.example.ShoppingSystem.quota.IpCountryLocalCacheStore;
import com.example.ShoppingSystem.quota.IpRiskLocalCacheStore;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpL6CountingBloomDecisionService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminRiskCreditScoreService {
    public AdminIpRiskListResponse listIpRiskProfiles(String family,
                                                      String country,
                                                      String level,
                                                      int page,
                                                      int pageSize,
                                                      String sort,
                                                      String q);

    public AdminIpRiskBatchUpdateResponse batchUpdateIpRiskScores(String family,
                                                                   AdminIpRiskBatchUpdateRequest request);

    public AdminDeviceRiskListResponse listDeviceRiskProfiles(String level, int page, int pageSize, String sort, String q);

    public AdminDeviceRiskDetailResponse getDeviceDetail(String deviceId);
}
