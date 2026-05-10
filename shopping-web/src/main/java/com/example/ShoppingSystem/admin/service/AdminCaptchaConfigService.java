package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaConfigField;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminCaptchaProviderConfigResponse;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminCaptchaConfigService {

    private static final String YAML_RESOURCE = "application.yaml";
    private static final String YAML_DISPLAY_PATH = "shopping-web/src/main/resources/application.yaml";
    private static final String WINDOWS_ENV_PROPERTY_SOURCE = AdminOAuth2WindowsEnvPostProcessor.PROPERTY_SOURCE_NAME;
    private static final String PROVIDER_TURNSTILE = "turnstile";
    private static final String PROVIDER_HCAPTCHA = "hcaptcha";
    private static final String PROVIDER_RECAPTCHA = "recaptcha";
    private static final String CAPTCHA_TURNSTILE_PATH = "captcha.turnstile";
    private static final String CAPTCHA_HCAPTCHA_PATH = "captcha.hcaptcha";
    private static final String CAPTCHA_RECAPTCHA_PATH = "captcha.recaptcha";
    private static final String YAML_SITE_KEY = "site-key";
    private static final String YAML_SECRET_KEY = "secret-key";
    private static final String NOT_CONFIGURED = "未配置";
    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");

    private final ConfigurableEnvironment environment;
    private final AdminWindowsSystemEnvService windowsSystemEnvService;
    private final Map<String, Object> windowsEnvValues = new ConcurrentHashMap<>();
    private final Object monitor = new Object();

    public AdminCaptchaConfigService(ConfigurableEnvironment environment,
                                     AdminWindowsSystemEnvService windowsSystemEnvService) {
        this.environment = environment;
        this.windowsSystemEnvService = windowsSystemEnvService;
        loadManagedEnvPropertySource();
    }

    public AdminCaptchaProviderConfigResponse turnstileConfig() {
        return providerConfig(providerDefinition(PROVIDER_TURNSTILE));
    }

    public AdminCaptchaProviderConfigResponse hcaptchaConfig() {
        return providerConfig(providerDefinition(PROVIDER_HCAPTCHA));
    }

    public AdminCaptchaProviderConfigResponse recaptchaConfig() {
        return providerConfig(providerDefinition(PROVIDER_RECAPTCHA));
    }

    public AdminCaptchaProviderConfigResponse updateConfig(String provider,
                                                           AdminCaptchaConfigUpdateRequest request) {
        ProviderDefinition definition = providerDefinition(provider);
        String siteKey = normalizeUpdateValue(request == null ? null : request.siteKey());
        String secretKey = normalizeUpdateValue(request == null ? null : request.secretKey());
        if (siteKey == null && secretKey == null) {
            throw new AdminServiceException(
                    "ADMIN_CAPTCHA_CONFIG_EMPTY",
                    "请至少填写一个需要修改的验证码配置值。",
                    HttpStatus.BAD_REQUEST
            );
        }

        synchronized (monitor) {
            if (siteKey != null) {
                writeWindowsSystemEnv(definition.siteKeyEnv(), siteKey);
            }
            if (secretKey != null) {
                writeWindowsSystemEnv(definition.secretKeyEnv(), secretKey);
            }
            windowsEnvValues.clear();
            windowsEnvValues.putAll(readManagedEnvValues());
            refreshManagedEnvPropertySource();
        }
        return providerConfig(definition);
    }

    private AdminCaptchaProviderConfigResponse providerConfig(ProviderDefinition definition) {
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata(definition.configPath());
        return new AdminCaptchaProviderConfigResponse(
                definition.provider(),
                buildField(
                        definition.siteKeyProperty(),
                        definition.siteKeyEnv(),
                        yamlMetadata.get(YAML_SITE_KEY)
                ),
                buildField(
                        definition.secretKeyProperty(),
                        definition.secretKeyEnv(),
                        yamlMetadata.get(YAML_SECRET_KEY)
                ),
                windowsSystemEnvService.windowsEnvTarget(),
                true
        );
    }

    private AdminCaptchaConfigField buildField(String propertyKey,
                                               String fallbackEnvName,
                                               AdminYamlFieldMetadata metadata) {
        String envName = metadata != null && StringUtils.hasText(metadata.envName())
                ? metadata.envName()
                : fallbackEnvName;
        Integer yamlLine = metadata == null ? null : metadata.yamlLine();
        String rawValue = resolveProperty(propertyKey);
        return new AdminCaptchaConfigField(
                maskValue(rawValue),
                propertyKey,
                envName,
                windowsSystemEnvService.windowsEnvTarget(),
                YAML_DISPLAY_PATH,
                yamlLine
        );
    }

    private String resolveProperty(String propertyKey) {
        try {
            return environment.getProperty(propertyKey);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, AdminYamlFieldMetadata> yamlMetadata(String configPath) {
        Resource resource = new ClassPathResource(YAML_RESOURCE);
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parseYamlMetadata(reader.lines().toList(), configPath);
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private Map<String, AdminYamlFieldMetadata> parseYamlMetadata(List<String> lines, String configPath) {
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
            if (configPath.equals(parentPath)
                    && (YAML_SITE_KEY.equals(key) || YAML_SECRET_KEY.equals(key))) {
                result.put(key, new AdminYamlFieldMetadata(index + 1, extractEnvName(value)));
                if (result.size() == 2) {
                    return result;
                }
            }
            if (!StringUtils.hasText(value)) {
                pathByIndent.put(indent, key);
            }
        }
        return result;
    }

    private int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count += 1;
        }
        return count;
    }

    private String extractEnvName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        Matcher matcher = ENV_PLACEHOLDER_PATTERN.matcher(value.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String displayValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return NOT_CONFIGURED;
        }
        String value = rawValue.trim();
        if (value.contains("${")) {
            return NOT_CONFIGURED;
        }
        return value;
    }

    private String maskValue(String rawValue) {
        String value = displayValue(rawValue);
        if (NOT_CONFIGURED.equals(value)) {
            return value;
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

    private ProviderDefinition providerDefinition(String provider) {
        return switch (provider == null ? "" : provider.trim().toLowerCase()) {
            case PROVIDER_TURNSTILE -> new ProviderDefinition(
                    PROVIDER_TURNSTILE,
                    CAPTCHA_TURNSTILE_PATH,
                    CAPTCHA_TURNSTILE_PATH + ".site-key",
                    "TURNSTILE_SITE_KEY",
                    CAPTCHA_TURNSTILE_PATH + ".secret-key",
                    "TURNSTILE_SECRET_KEY"
            );
            case PROVIDER_HCAPTCHA -> new ProviderDefinition(
                    PROVIDER_HCAPTCHA,
                    CAPTCHA_HCAPTCHA_PATH,
                    CAPTCHA_HCAPTCHA_PATH + ".site-key",
                    "HCAPTCHA_SITE_KEY",
                    CAPTCHA_HCAPTCHA_PATH + ".secret-key",
                    "HCAPTCHA_SECRET_KEY"
            );
            case PROVIDER_RECAPTCHA -> new ProviderDefinition(
                    PROVIDER_RECAPTCHA,
                    CAPTCHA_RECAPTCHA_PATH,
                    CAPTCHA_RECAPTCHA_PATH + ".site-key",
                    "RECAPTCHA_SITE_KEY",
                    CAPTCHA_RECAPTCHA_PATH + ".secret-key",
                    "RECAPTCHA_SECRET_KEY"
            );
            default -> throw new AdminServiceException(
                    "ADMIN_CAPTCHA_PROVIDER_UNSUPPORTED",
                    "不支持的验证码服务。",
                    HttpStatus.BAD_REQUEST
            );
        };
    }

    private void loadManagedEnvPropertySource() {
        synchronized (monitor) {
            windowsEnvValues.clear();
            windowsEnvValues.putAll(readManagedEnvValues());
            refreshManagedEnvPropertySource();
        }
    }

    private void refreshManagedEnvPropertySource() {
        MutablePropertySources propertySources = environment.getPropertySources();
        if (propertySources.contains(WINDOWS_ENV_PROPERTY_SOURCE)) {
            propertySources.remove(WINDOWS_ENV_PROPERTY_SOURCE);
        }
        propertySources.addFirst(new MapPropertySource(WINDOWS_ENV_PROPERTY_SOURCE, windowsEnvValues));
    }

    private Map<String, String> readManagedEnvValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String envName : AdminOAuth2WindowsEnvPostProcessor.MANAGED_ENV_NAMES) {
            windowsSystemEnvService.readSystemEnvValue(envName)
                    .ifPresent(value -> values.put(envName, value));
        }
        return values;
    }

    private void writeWindowsSystemEnv(String envName, String value) {
        windowsSystemEnvService.writeWindowsSystemEnv(
                envName,
                value,
                "ADMIN_CAPTCHA_WINDOWS_ENV_UNSUPPORTED",
                "ADMIN_CAPTCHA_WINDOWS_ENV_WRITE_FAILED",
                "ADMIN_CAPTCHA_WINDOWS_ENV_WRITE_INTERRUPTED"
        );
    }

    private String normalizeUpdateValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new AdminServiceException(
                    "ADMIN_CAPTCHA_CONFIG_INVALID",
                    "验证码配置值不能包含换行。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (trimmed.length() > 1024) {
            throw new AdminServiceException(
                    "ADMIN_CAPTCHA_CONFIG_TOO_LONG",
                    "验证码配置值长度不能超过 1024 个字符。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return trimmed;
    }

    private record AdminYamlFieldMetadata(Integer yamlLine, String envName) {
    }

    private record ProviderDefinition(String provider,
                                      String configPath,
                                      String siteKeyProperty,
                                      String siteKeyEnv,
                                      String secretKeyProperty,
                                      String secretKeyEnv) {
    }
}
