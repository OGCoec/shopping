package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigField;
import com.example.ShoppingSystem.admin.dto.AdminSmsConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmsProviderConfigResponse;
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
public class AdminSmsConfigService {

    private static final String PROVIDER_ALIYUN = "aliyun";
    private static final String DISPLAY_NAME = "阿里云 Dypnsapi 短信服务";
    private static final String DESCRIPTION = "手机号验证码发送";
    private static final String YAML_RESOURCE = "application.yaml";
    private static final String YAML_DISPLAY_PATH = "shopping-web/src/main/resources/application.yaml";
    private static final String WINDOWS_ENV_PROPERTY_SOURCE = "adminSmsWindowsEnv";
    private static final String WINDOWS_ENV_TARGET = AdminOAuth2WindowsEnvPostProcessor.WINDOWS_ENV_TARGET;
    private static final String ALIYUN_SMS_PATH = "aliyun.sms";
    private static final String ACCESS_KEY_ID_PROPERTY = ALIYUN_SMS_PATH + ".access-key-id";
    private static final String ACCESS_KEY_SECRET_PROPERTY = ALIYUN_SMS_PATH + ".access-key-secret";
    private static final String ACCESS_KEY_ID_ENV = "ALIBABA_CLOUD_ACCESS_KEY_ID";
    private static final String ACCESS_KEY_SECRET_ENV = "ALIBABA_CLOUD_ACCESS_KEY_SECRET";
    private static final String YAML_ACCESS_KEY_ID_KEY = "access-key-id";
    private static final String YAML_ACCESS_KEY_SECRET_KEY = "access-key-secret";
    private static final String NOT_CONFIGURED = "未配置";
    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");

    private final ConfigurableEnvironment environment;
    private final Map<String, Object> windowsEnvValues = new ConcurrentHashMap<>();
    private final Object monitor = new Object();

    public AdminSmsConfigService(ConfigurableEnvironment environment) {
        this.environment = environment;
        loadWindowsEnvPropertySource();
    }

    public AdminSmsProviderConfigResponse aliyunConfig() {
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata();
        return new AdminSmsProviderConfigResponse(
                PROVIDER_ALIYUN,
                DISPLAY_NAME,
                DESCRIPTION,
                buildField(
                        ACCESS_KEY_ID_PROPERTY,
                        ACCESS_KEY_ID_ENV,
                        yamlMetadata.get(YAML_ACCESS_KEY_ID_KEY)
                ),
                buildField(
                        ACCESS_KEY_SECRET_PROPERTY,
                        ACCESS_KEY_SECRET_ENV,
                        yamlMetadata.get(YAML_ACCESS_KEY_SECRET_KEY)
                ),
                WINDOWS_ENV_TARGET,
                true
        );
    }

    public AdminSmsProviderConfigResponse updateAliyunConfig(AdminSmsConfigUpdateRequest request) {
        String accessKeyId = normalizeUpdateValue(request == null ? null : request.accessKeyId());
        String accessKeySecret = normalizeUpdateValue(request == null ? null : request.accessKeySecret());
        if (accessKeyId == null && accessKeySecret == null) {
            throw new AdminServiceException(
                    "ADMIN_SMS_CONFIG_EMPTY",
                    "请至少填写一个需要修改的短信服务配置值。",
                    HttpStatus.BAD_REQUEST
            );
        }
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata();
        String accessKeyIdEnv = resolveEnvName(yamlMetadata.get(YAML_ACCESS_KEY_ID_KEY), ACCESS_KEY_ID_ENV);
        String accessKeySecretEnv = resolveEnvName(yamlMetadata.get(YAML_ACCESS_KEY_SECRET_KEY), ACCESS_KEY_SECRET_ENV);
        synchronized (monitor) {
            if (accessKeyId != null) {
                writeWindowsSystemEnv(accessKeyIdEnv, accessKeyId);
                windowsEnvValues.put(accessKeyIdEnv, accessKeyId);
            }
            if (accessKeySecret != null) {
                writeWindowsSystemEnv(accessKeySecretEnv, accessKeySecret);
                windowsEnvValues.put(accessKeySecretEnv, accessKeySecret);
            }
            refreshWindowsEnvPropertySource();
        }
        return aliyunConfig();
    }

    private AdminOAuth2ConfigField buildField(String propertyKey,
                                             String fallbackEnvName,
                                             AdminYamlFieldMetadata metadata) {
        String envName = resolveEnvName(metadata, fallbackEnvName);
        Integer yamlLine = metadata == null ? null : metadata.yamlLine();
        String rawValue = readProperty(propertyKey);
        return new AdminOAuth2ConfigField(
                maskValue(rawValue),
                propertyKey,
                envName,
                WINDOWS_ENV_TARGET,
                YAML_DISPLAY_PATH,
                yamlLine
        );
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
            if (ALIYUN_SMS_PATH.equals(parentPath)
                    && (YAML_ACCESS_KEY_ID_KEY.equals(key) || YAML_ACCESS_KEY_SECRET_KEY.equals(key))) {
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

    private String resolveEnvName(AdminYamlFieldMetadata metadata, String fallbackEnvName) {
        return metadata != null && StringUtils.hasText(metadata.envName())
                ? metadata.envName()
                : fallbackEnvName;
    }

    private String readProperty(String propertyKey) {
        try {
            return environment.getProperty(propertyKey);
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata();
        String accessKeyIdEnv = resolveEnvName(yamlMetadata.get(YAML_ACCESS_KEY_ID_KEY), ACCESS_KEY_ID_ENV);
        String accessKeySecretEnv = resolveEnvName(yamlMetadata.get(YAML_ACCESS_KEY_SECRET_KEY), ACCESS_KEY_SECRET_ENV);
        AdminOAuth2WindowsEnvPostProcessor.readWindowsSystemEnvValue(accessKeyIdEnv)
                .ifPresent(value -> values.put(accessKeyIdEnv, value));
        AdminOAuth2WindowsEnvPostProcessor.readWindowsSystemEnvValue(accessKeySecretEnv)
                .ifPresent(value -> values.put(accessKeySecretEnv, value));
        for (String envName : AdminOAuth2WindowsEnvPostProcessor.SMS_ENV_NAMES) {
            AdminOAuth2WindowsEnvPostProcessor.readWindowsSystemEnvValue(envName)
                    .ifPresent(value -> values.put(envName, value));
        }
        return values;
    }

    private void writeWindowsSystemEnv(String envName, String value) {
        if (!AdminOAuth2WindowsEnvPostProcessor.isWindows()) {
            throw new AdminServiceException(
                    "ADMIN_SMS_WINDOWS_ENV_UNSUPPORTED",
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
                        "ADMIN_SMS_WINDOWS_ENV_WRITE_FAILED",
                        "写入 Windows 系统环境变量失败，请以管理员身份启动 Spring Boot 后重试：" + output.trim(),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (IOException ex) {
            throw new AdminServiceException(
                    "ADMIN_SMS_WINDOWS_ENV_WRITE_FAILED",
                    "写入 Windows 系统环境变量失败。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AdminServiceException(
                    "ADMIN_SMS_WINDOWS_ENV_WRITE_INTERRUPTED",
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
                    "ADMIN_SMS_CONFIG_INVALID",
                    "短信服务配置值不能包含换行。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (trimmed.length() > 1024) {
            throw new AdminServiceException(
                    "ADMIN_SMS_CONFIG_TOO_LONG",
                    "短信服务配置值长度不能超过 1024 个字符。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return trimmed;
    }

    private record AdminYamlFieldMetadata(Integer yamlLine, String envName) {
    }
}
