package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigField;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ProviderConfigResponse;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminOAuth2ConfigService {

    private static final String PROVIDER_GITHUB = "github";
    private static final String YAML_RESOURCE = "application.yaml";
    private static final String YAML_DISPLAY_PATH = "shopping-web/src/main/resources/application.yaml";
    private static final String WINDOWS_ENV_PROPERTY_SOURCE = AdminOAuth2WindowsEnvPostProcessor.PROPERTY_SOURCE_NAME;
    private static final String WINDOWS_ENV_TARGET = AdminOAuth2WindowsEnvPostProcessor.WINDOWS_ENV_TARGET;
    private static final String GITHUB_REGISTRATION_PATH = "spring.security.oauth2.client.registration.github";
    private static final String GITHUB_CLIENT_ID_PROPERTY = GITHUB_REGISTRATION_PATH + ".client-id";
    private static final String GITHUB_CLIENT_SECRET_PROPERTY = GITHUB_REGISTRATION_PATH + ".client-secret";
    private static final String GITHUB_CLIENT_ID_ENV = "GITHUB_CLIENT_ID";
    private static final String GITHUB_CLIENT_SECRET_ENV = "GITHUB_CLIENT_SECRET";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String GOOGLE_REGISTRATION_PATH = "spring.security.oauth2.client.registration.google";
    private static final String GOOGLE_CLIENT_ID_PROPERTY = GOOGLE_REGISTRATION_PATH + ".client-id";
    private static final String GOOGLE_CLIENT_SECRET_PROPERTY = GOOGLE_REGISTRATION_PATH + ".client-secret";
    private static final String GOOGLE_CLIENT_ID_ENV = "GOOGLE_CLIENT_ID";
    private static final String GOOGLE_CLIENT_SECRET_ENV = "GOOGLE_CLIENT_SECRET";
    private static final String PROVIDER_MICROSOFT = "microsoft";
    private static final String MICROSOFT_REGISTRATION_PATH = "spring.security.oauth2.client.registration.microsoft";
    private static final String MICROSOFT_CLIENT_ID_PROPERTY = MICROSOFT_REGISTRATION_PATH + ".client-id";
    private static final String MICROSOFT_CLIENT_SECRET_PROPERTY = MICROSOFT_REGISTRATION_PATH + ".client-secret";
    private static final String MICROSOFT_CLIENT_ID_ENV = "AZURE_CLIENT_ID";
    private static final String MICROSOFT_CLIENT_SECRET_ENV = "AZURE_CLIENT_SECRET";
    private static final String YAML_CLIENT_ID_KEY = "client-id";
    private static final String YAML_CLIENT_SECRET_KEY = "client-secret";
    private static final String NOT_CONFIGURED = "未配置";
    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");

    private final ConfigurableEnvironment environment;
    private final Map<String, Object> windowsEnvValues = new ConcurrentHashMap<>();
    private final Object monitor = new Object();

    public AdminOAuth2ConfigService(ConfigurableEnvironment environment) {
        this.environment = environment;
        loadWindowsEnvPropertySource();
    }

    public AdminOAuth2ProviderConfigResponse githubConfig() {
        return providerConfig(
                PROVIDER_GITHUB,
                GITHUB_REGISTRATION_PATH,
                GITHUB_CLIENT_ID_PROPERTY,
                GITHUB_CLIENT_ID_ENV,
                GITHUB_CLIENT_SECRET_PROPERTY,
                GITHUB_CLIENT_SECRET_ENV
        );
    }

    public AdminOAuth2ProviderConfigResponse googleConfig() {
        return providerConfig(
                PROVIDER_GOOGLE,
                GOOGLE_REGISTRATION_PATH,
                GOOGLE_CLIENT_ID_PROPERTY,
                GOOGLE_CLIENT_ID_ENV,
                GOOGLE_CLIENT_SECRET_PROPERTY,
                GOOGLE_CLIENT_SECRET_ENV
        );
    }

    public AdminOAuth2ProviderConfigResponse microsoftConfig() {
        return providerConfig(
                PROVIDER_MICROSOFT,
                MICROSOFT_REGISTRATION_PATH,
                MICROSOFT_CLIENT_ID_PROPERTY,
                MICROSOFT_CLIENT_ID_ENV,
                MICROSOFT_CLIENT_SECRET_PROPERTY,
                MICROSOFT_CLIENT_SECRET_ENV
        );
    }

    public AdminOAuth2ProviderConfigResponse updateConfig(String provider,
                                                          AdminOAuth2ConfigUpdateRequest request) {
        ProviderDefinition definition = providerDefinition(provider);
        String clientId = normalizeUpdateValue(request == null ? null : request.clientId());
        String clientSecret = normalizeUpdateValue(request == null ? null : request.clientSecret());
        if (clientId == null && clientSecret == null) {
            throw new AdminServiceException(
                    "ADMIN_OAUTH2_CONFIG_EMPTY",
                    "请至少填写一个需要修改的 OAuth2 配置值。",
                    HttpStatus.BAD_REQUEST
            );
        }
        synchronized (monitor) {
            if (clientId != null) {
                writeWindowsSystemEnv(definition.clientIdEnv(), clientId);
            }
            if (clientSecret != null) {
                writeWindowsSystemEnv(definition.clientSecretEnv(), clientSecret);
            }
            windowsEnvValues.clear();
            windowsEnvValues.putAll(readWindowsSystemEnv());
            refreshWindowsEnvPropertySource();
        }
        return providerConfig(definition);
    }

    private AdminOAuth2ProviderConfigResponse providerConfig(String provider,
                                                            String registrationPath,
                                                            String clientIdProperty,
                                                            String clientIdEnv,
                                                            String clientSecretProperty,
                                                            String clientSecretEnv) {
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata(registrationPath);
        return new AdminOAuth2ProviderConfigResponse(
                provider,
                buildField(
                        clientIdProperty,
                        clientIdEnv,
                        yamlMetadata.get(YAML_CLIENT_ID_KEY),
                        true
                ),
                buildField(
                        clientSecretProperty,
                        clientSecretEnv,
                        yamlMetadata.get(YAML_CLIENT_SECRET_KEY),
                        true
                ),
                WINDOWS_ENV_TARGET,
                true
        );
    }

    private AdminOAuth2ProviderConfigResponse providerConfig(ProviderDefinition definition) {
        return providerConfig(
                definition.provider(),
                definition.registrationPath(),
                definition.clientIdProperty(),
                definition.clientIdEnv(),
                definition.clientSecretProperty(),
                definition.clientSecretEnv()
        );
    }

    private AdminOAuth2ConfigField buildField(String propertyKey,
                                             String fallbackEnvName,
                                             AdminYamlFieldMetadata metadata,
                                             boolean sensitive) {
        String envName = metadata != null && StringUtils.hasText(metadata.envName())
                ? metadata.envName()
                : fallbackEnvName;
        Integer yamlLine = metadata == null ? null : metadata.yamlLine();
        String rawValue = environment.getProperty(propertyKey);
        return new AdminOAuth2ConfigField(
                sensitive ? maskValue(rawValue) : displayValue(rawValue),
                propertyKey,
                envName,
                WINDOWS_ENV_TARGET,
                YAML_DISPLAY_PATH,
                yamlLine
        );
    }

    private Map<String, AdminYamlFieldMetadata> yamlMetadata(String registrationPath) {
        Resource resource = new ClassPathResource(YAML_RESOURCE);
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parseYamlMetadata(reader.lines().toList(), registrationPath);
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private Map<String, AdminYamlFieldMetadata> parseYamlMetadata(List<String> lines, String registrationPath) {
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
            if (registrationPath.equals(parentPath)
                    && (YAML_CLIENT_ID_KEY.equals(key) || YAML_CLIENT_SECRET_KEY.equals(key))) {
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
            case PROVIDER_GITHUB -> new ProviderDefinition(
                    PROVIDER_GITHUB,
                    GITHUB_REGISTRATION_PATH,
                    GITHUB_CLIENT_ID_PROPERTY,
                    GITHUB_CLIENT_ID_ENV,
                    GITHUB_CLIENT_SECRET_PROPERTY,
                    GITHUB_CLIENT_SECRET_ENV
            );
            case PROVIDER_GOOGLE -> new ProviderDefinition(
                    PROVIDER_GOOGLE,
                    GOOGLE_REGISTRATION_PATH,
                    GOOGLE_CLIENT_ID_PROPERTY,
                    GOOGLE_CLIENT_ID_ENV,
                    GOOGLE_CLIENT_SECRET_PROPERTY,
                    GOOGLE_CLIENT_SECRET_ENV
            );
            case PROVIDER_MICROSOFT -> new ProviderDefinition(
                    PROVIDER_MICROSOFT,
                    MICROSOFT_REGISTRATION_PATH,
                    MICROSOFT_CLIENT_ID_PROPERTY,
                    MICROSOFT_CLIENT_ID_ENV,
                    MICROSOFT_CLIENT_SECRET_PROPERTY,
                    MICROSOFT_CLIENT_SECRET_ENV
            );
            default -> throw new AdminServiceException(
                    "ADMIN_OAUTH2_PROVIDER_UNSUPPORTED",
                    "不支持的 OAuth2 服务。",
                    HttpStatus.BAD_REQUEST
            );
        };
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
                    "ADMIN_OAUTH2_WINDOWS_ENV_UNSUPPORTED",
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
                        "ADMIN_OAUTH2_WINDOWS_ENV_WRITE_FAILED",
                        "写入 Windows 系统环境变量失败，请确认 Spring Boot 以管理员身份运行：" + output.trim(),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (IOException ex) {
            throw new AdminServiceException(
                    "ADMIN_OAUTH2_WINDOWS_ENV_WRITE_FAILED",
                    "写入 Windows 系统环境变量失败。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AdminServiceException(
                    "ADMIN_OAUTH2_WINDOWS_ENV_WRITE_INTERRUPTED",
                    "写入 Windows 系统环境变量被中断。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String normalizeUpdateValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new AdminServiceException(
                    "ADMIN_OAUTH2_CONFIG_INVALID",
                    "OAuth2 配置值不能包含换行。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (trimmed.length() > 1024) {
            throw new AdminServiceException(
                    "ADMIN_OAUTH2_CONFIG_TOO_LONG",
                    "OAuth2 配置值长度不能超过 1024 个字符。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return trimmed;
    }

    private record AdminYamlFieldMetadata(Integer yamlLine, String envName) {
    }

    private record ProviderDefinition(String provider,
                                      String registrationPath,
                                      String clientIdProperty,
                                      String clientIdEnv,
                                      String clientSecretProperty,
                                      String clientSecretEnv) {
    }
}
