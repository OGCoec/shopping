package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
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
import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys;
import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys.AccountType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminRiskApiConfigService {

    private static final String PROVIDER_IP2LOCATION = "ip2location";
    private static final String PROVIDER_IPING = "iping";
    private static final String YAML_RESOURCE = "application.yaml";
    private static final String YAML_DISPLAY_PATH = "shopping-web/src/main/resources/application.yaml";
    private static final String WINDOWS_ENV_PROPERTY_SOURCE = AdminOAuth2WindowsEnvPostProcessor.PROPERTY_SOURCE_NAME;
    private static final String WINDOWS_ENV_TARGET = AdminOAuth2WindowsEnvPostProcessor.WINDOWS_ENV_TARGET;
    private static final String IP2LOCATION_QUOTA_REDIS_DATABASE = "2";
    private static final String NOT_CONFIGURED = "未配置";
    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");
    private static final List<ProviderDefinition> PROVIDERS = List.of(
            new ProviderDefinition(
                    PROVIDER_IP2LOCATION,
                    "IP2Location API",
                    "ip2location.io",
                    List.of(new FieldDefinition(
                            "apiUrl",
                            "API 地址",
                            "ip2location.io.api-url",
                            "IP2LOCATION_IO_API_URL",
                            DisplayMode.URL,
                            true
                    ))
            ),
            new ProviderDefinition(
                    PROVIDER_IPING,
                    "iPing 降级 API",
                    "iping.api",
                    List.of(
                            new FieldDefinition(
                                    "enabled",
                                    "启用状态",
                                    "iping.api.enabled",
                                    "IPING_API_ENABLED",
                                    DisplayMode.BOOLEAN,
                                    false
                            ),
                            new FieldDefinition(
                                    "apiUrl",
                                    "API 地址",
                                    "iping.api.url",
                                    "IPING_API_URL",
                                    DisplayMode.URL,
                                    true
                            ),
                            new FieldDefinition(
                                    "language",
                                    "返回语言",
                                    "iping.api.language",
                                    "IPING_API_LANGUAGE",
                                    DisplayMode.PLAIN,
                                    false
                            )
                    )
            )
    );

    private final ConfigurableEnvironment environment;
    private final Ip2LocationQuotaService ip2LocationQuotaService;
    private final Map<String, Object> windowsEnvValues = new ConcurrentHashMap<>();
    private final Object monitor = new Object();

    public AdminRiskApiConfigService(ConfigurableEnvironment environment,
                                     Ip2LocationQuotaService ip2LocationQuotaService) {
        this.environment = environment;
        this.ip2LocationQuotaService = ip2LocationQuotaService;
        loadWindowsEnvPropertySource();
    }

    public AdminRiskApiProviderConfigResponse providerConfig(String provider) {
        ProviderDefinition definition = providerDefinition(provider);
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata();
        List<AdminRiskApiConfigField> fields = definition.fields().stream()
                .map(field -> buildField(field, yamlMetadata))
                .toList();
        return new AdminRiskApiProviderConfigResponse(
                definition.provider(),
                definition.displayName(),
                definition.propertyPrefix(),
                fields,
                WINDOWS_ENV_TARGET,
                true
        );
    }

    public AdminRiskApiProviderConfigResponse updateConfig(String provider, AdminRiskApiConfigUpdateRequest request) {
        ProviderDefinition definition = providerDefinition(provider);
        Map<String, String> values = request == null || request.values() == null ? Map.of() : request.values();
        Map<FieldDefinition, String> updates = new LinkedHashMap<>();
        for (FieldDefinition field : definition.fields()) {
            String normalizedValue = normalizeUpdateValue(field, values.get(field.id()));
            if (normalizedValue != null) {
                updates.put(field, normalizedValue);
            }
        }
        if (updates.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_CONFIG_EMPTY",
                    "请至少填写一个需要修改的 Risk API 配置值。",
                    HttpStatus.BAD_REQUEST
            );
        }
        synchronized (monitor) {
            updates.forEach((field, value) -> {
                writeWindowsSystemEnv(field.envName(), value);
                windowsEnvValues.put(field.envName(), value);
            });
            refreshWindowsEnvPropertySource();
        }
        return providerConfig(definition.provider());
    }

    public AdminIp2LocationQuotaKeysResponse ip2LocationQuotaKeys() {
        Ip2LocationQuotaService.QuotaKeyListResult result = ip2LocationQuotaService.listQuotaKeys();
        List<AdminIp2LocationQuotaKeyItem> keys = result.quotaKeys().stream()
                .map(this::toQuotaKeyItem)
                .toList();
        return new AdminIp2LocationQuotaKeysResponse(
                IP2LOCATION_QUOTA_REDIS_DATABASE,
                Ip2LocationQuotaRedisKeys.QUOTA_PREFIX,
                result.aggregateTotalQuotaCount(),
                result.realTotalQuotaCount(),
                keys
        );
    }

    public AdminIp2LocationQuotaBatchResult batchAddIp2LocationQuotaKeys(AdminIp2LocationQuotaBatchAddRequest request) {
        List<AdminIp2LocationQuotaBatchAddItem> items = request == null || request.items() == null
                ? List.of()
                : request.items();
        Map<String, Ip2LocationQuotaAddDraft> drafts = new LinkedHashMap<>();
        for (AdminIp2LocationQuotaBatchAddItem item : items) {
            Ip2LocationQuotaAddDraft draft = toAddDraft(item);
            drafts.put(draft.apiKey(), draft);
        }
        if (drafts.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_IP2LOCATION_KEYS_EMPTY",
                    "请至少填写一个 IP2Location API key。",
                    HttpStatus.BAD_REQUEST
            );
        }
        LocalDateTime now = LocalDateTime.now();
        List<Ip2LocationQuotaService.QuotaKeyUpsertCommand> commands = new ArrayList<>(drafts.size());
        for (Ip2LocationQuotaAddDraft draft : drafts.values()) {
            String quotaKey = ip2LocationQuotaService.buildQuotaKey(draft.apiKey(), now, draft.accountType());
            Duration ttl = ip2LocationQuotaService.resolveQuotaTtl(draft.accountType());
            long ttlSeconds = ttl == null || ttl.isZero() || ttl.isNegative() ? -1L : ttl.toSeconds();
            commands.add(new Ip2LocationQuotaService.QuotaKeyUpsertCommand(
                    draft.apiKey(),
                    quotaKey,
                    draft.remainingQuota(),
                    ttlSeconds
            ));
        }
        Ip2LocationQuotaService.QuotaBatchUpsertResult result = ip2LocationQuotaService.batchUpsertQuotaKeys(commands);
        return new AdminIp2LocationQuotaBatchResult(
                drafts.size(),
                result.upsertedCount(),
                result.oldDeletedCount(),
                result.totalQuotaCount()
        );
    }

    public AdminIp2LocationQuotaBatchResult batchDeleteIp2LocationQuotaKeys(AdminIp2LocationQuotaBatchDeleteRequest request) {
        List<String> redisKeys = request == null || request.redisKeys() == null
                ? List.of()
                : request.redisKeys().stream()
                .map(key -> key == null ? "" : key.trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (redisKeys.isEmpty()) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_IP2LOCATION_DELETE_EMPTY",
                    "请选择需要删除的 IP2Location API key。",
                    HttpStatus.BAD_REQUEST
            );
        }
        redisKeys.forEach(this::validateQuotaRedisKey);
        Ip2LocationQuotaService.QuotaBatchDeleteResult result = ip2LocationQuotaService.batchDeleteQuotaKeys(redisKeys);
        return new AdminIp2LocationQuotaBatchResult(
                redisKeys.size(),
                result.deletedCount(),
                0,
                result.totalQuotaCount()
        );
    }

    private AdminIp2LocationQuotaKeyItem toQuotaKeyItem(Ip2LocationQuotaService.QuotaKeySnapshot snapshot) {
        ParsedQuotaKey parsed = parseQuotaKey(snapshot.quotaKey());
        return new AdminIp2LocationQuotaKeyItem(
                snapshot.quotaKey(),
                parsed.apiKey(),
                parsed.accountType(),
                parsed.createdAtMinute(),
                snapshot.remainingQuota(),
                snapshot.ttlSeconds()
        );
    }

    private ParsedQuotaKey parseQuotaKey(String quotaKey) {
        if (!StringUtils.hasText(quotaKey)) {
            return new ParsedQuotaKey("", "", "");
        }
        String safeQuotaKey = quotaKey.trim();
        if (!safeQuotaKey.startsWith(Ip2LocationQuotaRedisKeys.QUOTA_PREFIX)) {
            return new ParsedQuotaKey(
                    Ip2LocationQuotaRedisKeys.extractApiKey(safeQuotaKey),
                    "",
                    ""
            );
        }
        String payload = safeQuotaKey.substring(Ip2LocationQuotaRedisKeys.QUOTA_PREFIX.length());
        int accountTypeDelimiter = payload.indexOf(':');
        int apiKeyDelimiter = payload.lastIndexOf(':');
        if (accountTypeDelimiter <= 0 || apiKeyDelimiter <= accountTypeDelimiter) {
            return new ParsedQuotaKey(
                    Ip2LocationQuotaRedisKeys.extractApiKey(safeQuotaKey),
                    "",
                    ""
            );
        }
        String accountType = payload.substring(0, accountTypeDelimiter);
        String createdAtMinute = payload.substring(accountTypeDelimiter + 1, apiKeyDelimiter);
        String apiKey = payload.substring(apiKeyDelimiter + 1);
        return new ParsedQuotaKey(apiKey, accountType, createdAtMinute);
    }

    private Ip2LocationQuotaAddDraft toAddDraft(AdminIp2LocationQuotaBatchAddItem item) {
        String apiKey = normalizeApiKey(item == null ? null : item.apiKey());
        AccountType accountType = normalizeAccountType(item == null ? null : item.accountType());
        long remainingQuota = normalizeRemainingQuota(item == null ? null : item.remainingQuota(), accountType);
        return new Ip2LocationQuotaAddDraft(apiKey, accountType, remainingQuota);
    }

    private String normalizeApiKey(String rawApiKey) {
        if (!StringUtils.hasText(rawApiKey)) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_IP2LOCATION_KEY_INVALID",
                    "IP2Location API key 不能为空。",
                    HttpStatus.BAD_REQUEST
            );
        }
        String apiKey = rawApiKey.trim();
        if (apiKey.length() > 256 || apiKey.indexOf(':') >= 0 || apiKey.matches(".*\\s+.*")) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_IP2LOCATION_KEY_INVALID",
                    "IP2Location API key 不能包含空白字符或冒号，长度不能超过 256。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return apiKey;
    }

    private AccountType normalizeAccountType(String rawAccountType) {
        if (!StringUtils.hasText(rawAccountType)) {
            return AccountType.STARTER;
        }
        AccountType parsed = AccountType.parseOrNull(rawAccountType);
        if (parsed == null) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_IP2LOCATION_ACCOUNT_TYPE_INVALID",
                    "不支持的 IP2Location 账户类型。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return parsed;
    }

    private long normalizeRemainingQuota(Long remainingQuota, AccountType accountType) {
        if (remainingQuota == null) {
            return ip2LocationQuotaService.resolveQuotaAmount(accountType);
        }
        if (remainingQuota < 0L) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_IP2LOCATION_QUOTA_INVALID",
                    "IP2Location 剩余额度不能小于 0。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return remainingQuota;
    }

    private void validateQuotaRedisKey(String redisKey) {
        String safeRedisKey = redisKey == null ? "" : redisKey;
        String[] parts = safeRedisKey.split(":", 5);
        if (!safeRedisKey.startsWith(Ip2LocationQuotaRedisKeys.QUOTA_PREFIX)
                || Ip2LocationQuotaRedisKeys.QUOTA_COUNT_KEY.equals(safeRedisKey)
                || Ip2LocationQuotaRedisKeys.QUOTA_ROUND_ROBIN_CURSOR_KEY.equals(safeRedisKey)
                || parts.length < 5
                || AccountType.parseOrNull(parts[2]) == null
                || !StringUtils.hasText(parts[3])
                || !StringUtils.hasText(parts[4])) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_IP2LOCATION_REDIS_KEY_INVALID",
                    "只能删除 IP2Location quota API key。",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private AdminRiskApiConfigField buildField(FieldDefinition field,
                                               Map<String, AdminYamlFieldMetadata> yamlMetadata) {
        AdminYamlFieldMetadata metadata = yamlMetadata.get(field.propertyKey());
        String envName = metadata != null && StringUtils.hasText(metadata.envName())
                ? metadata.envName()
                : field.envName();
        return new AdminRiskApiConfigField(
                field.id(),
                field.label(),
                formatValue(readProperty(field.propertyKey()), field),
                field.propertyKey(),
                envName,
                WINDOWS_ENV_TARGET,
                metadata == null ? null : YAML_DISPLAY_PATH,
                metadata == null ? null : metadata.yamlLine(),
                field.sensitive()
        );
    }

    private String formatValue(String rawValue, FieldDefinition field) {
        if (!StringUtils.hasText(rawValue) || isUnresolvedPlaceholder(rawValue)) {
            return NOT_CONFIGURED;
        }
        String value = rawValue.trim();
        if (field.sensitive()) {
            return maskValue(value);
        }
        return switch (field.displayMode()) {
            case BOOLEAN -> formatBoolean(value);
            case URL, PLAIN -> value;
        };
    }

    private String formatBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "启用";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "关闭";
        }
        return value;
    }

    private String maskValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
            return NOT_CONFIGURED;
        }
        int length = value.length();
        if (length <= 4) {
            return "****";
        }
        if (length <= 8) {
            return value.substring(0, 2) + "****" + value.substring(length - 2);
        }
        return value.substring(0, 4) + "****" + value.substring(length - 4);
    }

    private String readProperty(String propertyKey) {
        try {
            return environment.getProperty(propertyKey);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeUpdateValue(FieldDefinition field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw invalidValue("Risk API 配置值不能包含换行。");
        }
        if (trimmed.length() > 1024) {
            throw invalidValue("Risk API 配置值长度不能超过 1024 个字符。");
        }
        switch (field.displayMode()) {
            case BOOLEAN -> validateBoolean(trimmed);
            case URL -> validateUrl(trimmed);
            case PLAIN -> validatePlainValue(trimmed);
        }
        return trimmed;
    }

    private void validateBoolean(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw invalidValue("启用状态只能填写 true 或 false。");
        }
    }

    private void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(uri.getHost())
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw invalidValue("API 地址必须是 http 或 https URL。");
            }
        } catch (IllegalArgumentException ex) {
            throw invalidValue("API 地址格式不正确。");
        }
    }

    private void validatePlainValue(String value) {
        if (!value.matches("[A-Za-z0-9_-]{1,32}")) {
            throw invalidValue("返回语言只能包含字母、数字、下划线或短横线，长度不能超过 32。");
        }
    }

    private AdminServiceException invalidValue(String message) {
        return new AdminServiceException(
                "ADMIN_RISK_API_CONFIG_INVALID",
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    private ProviderDefinition providerDefinition(String provider) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        return PROVIDERS.stream()
                .filter(definition -> definition.provider().equals(normalizedProvider))
                .findFirst()
                .orElseThrow(() -> new AdminServiceException(
                        "ADMIN_RISK_API_PROVIDER_UNSUPPORTED",
                        "不支持的 Risk API 服务。",
                        HttpStatus.BAD_REQUEST
                ));
    }

    private Map<String, AdminYamlFieldMetadata> yamlMetadata() {
        Resource resource = new ClassPathResource(YAML_RESOURCE);
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parseYamlMetadata(reader.lines().toList());
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private Map<String, AdminYamlFieldMetadata> parseYamlMetadata(List<String> lines) {
        Map<String, AdminYamlFieldMetadata> result = new HashMap<>();
        TreeMap<Integer, String> pathByIndent = new TreeMap<>();
        for (int index = 0; index < lines.size(); index += 1) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            int indent = leadingSpaces(line);
            pathByIndent.tailMap(indent, true).clear();

            String key = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();
            String parentPath = String.join(".", pathByIndent.values());
            String fullPath = StringUtils.hasText(parentPath) ? parentPath + "." + key : key;
            if (StringUtils.hasText(value)) {
                result.put(fullPath, new AdminYamlFieldMetadata(index + 1, extractEnvName(value)));
            }
            pathByIndent.put(indent, key);
        }
        return result;
    }

    private String extractEnvName(String yamlValue) {
        Matcher matcher = ENV_PLACEHOLDER_PATTERN.matcher(yamlValue);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count += 1;
        }
        return count;
    }

    private boolean isUnresolvedPlaceholder(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}");
    }

    private void loadWindowsEnvPropertySource() {
        synchronized (monitor) {
            windowsEnvValues.clear();
            windowsEnvValues.putAll(readWindowsSystemEnv());
            refreshWindowsEnvPropertySource();
        }
    }

    private void refreshWindowsEnvPropertySource() {
        MutablePropertySources propertySources = environment.getPropertySources();
        if (propertySources.contains(WINDOWS_ENV_PROPERTY_SOURCE)) {
            propertySources.remove(WINDOWS_ENV_PROPERTY_SOURCE);
        }
        propertySources.addFirst(new MapPropertySource(WINDOWS_ENV_PROPERTY_SOURCE, windowsEnvValues));
    }

    private Map<String, String> readWindowsSystemEnv() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!AdminOAuth2WindowsEnvPostProcessor.isWindows()) {
            return values;
        }
        for (String envName : AdminOAuth2WindowsEnvPostProcessor.MANAGED_ENV_NAMES) {
            AdminOAuth2WindowsEnvPostProcessor.readWindowsSystemEnvValue(envName)
                    .ifPresent(value -> values.put(envName, value));
        }
        return values;
    }

    private void writeWindowsSystemEnv(String envName, String value) {
        if (!AdminOAuth2WindowsEnvPostProcessor.isWindows()) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_WINDOWS_ENV_UNSUPPORTED",
                    "当前接口只支持写入 Windows 系统环境变量。",
                    HttpStatus.BAD_REQUEST
            );
        }
        try {
            Process process = new ProcessBuilder(
                    AdminOAuth2WindowsEnvPostProcessor.windowsTool("setx.exe"),
                    envName,
                    value,
                    "/M"
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                throw new AdminServiceException(
                        "ADMIN_RISK_API_WINDOWS_ENV_WRITE_FAILED",
                        "写入 Windows 系统环境变量失败，请确认 Spring Boot 以管理员身份运行：" + output.trim(),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (IOException ex) {
            throw new AdminServiceException(
                    "ADMIN_RISK_API_WINDOWS_ENV_WRITE_FAILED",
                    "写入 Windows 系统环境变量失败。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AdminServiceException(
                    "ADMIN_RISK_API_WINDOWS_ENV_WRITE_INTERRUPTED",
                    "写入 Windows 系统环境变量被中断。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private enum DisplayMode {
        BOOLEAN,
        PLAIN,
        URL
    }

    private record ProviderDefinition(String provider,
                                      String displayName,
                                      String propertyPrefix,
                                      List<FieldDefinition> fields) {
    }

    private record FieldDefinition(String id,
                                   String label,
                                   String propertyKey,
                                   String envName,
                                   DisplayMode displayMode,
                                   boolean sensitive) {
    }

    private record Ip2LocationQuotaAddDraft(String apiKey,
                                            AccountType accountType,
                                            long remainingQuota) {
    }

    private record ParsedQuotaKey(String apiKey,
                                  String accountType,
                                  String createdAtMinute) {
    }

    private record AdminYamlFieldMetadata(Integer yamlLine, String envName) {
    }
}
