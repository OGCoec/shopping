package com.example.ShoppingSystem.admin.service.config;

import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchAddItem;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchAddRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchDeleteRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaBatchResult;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaKeyItem;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationQuotaKeysResponse;
import com.example.ShoppingSystem.admin.dto.AdminRiskApiConfigField;
import com.example.ShoppingSystem.admin.dto.AdminRiskApiConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminRiskApiProviderConfigResponse;
import com.example.ShoppingSystem.quota.Ip2LocationQuotaService;
import com.example.ShoppingSystem.quota.RiskApiConfigStoreService;
import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys;
import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys.AccountType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminRiskApiConfigService {
    public AdminRiskApiProviderConfigResponse providerConfig(String provider);

    public AdminRiskApiProviderConfigResponse updateConfig(String provider, AdminRiskApiConfigUpdateRequest request);

    public AdminIp2LocationQuotaKeysResponse ip2LocationQuotaKeys();

    public AdminIp2LocationQuotaBatchResult batchAddIp2LocationQuotaKeys(AdminIp2LocationQuotaBatchAddRequest request);

    public AdminIp2LocationQuotaBatchResult batchDeleteIp2LocationQuotaKeys(AdminIp2LocationQuotaBatchDeleteRequest request);
}
