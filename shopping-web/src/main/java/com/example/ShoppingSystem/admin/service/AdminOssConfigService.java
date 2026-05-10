package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.dto.AdminOAuth2ConfigField;
import com.example.ShoppingSystem.admin.dto.AdminOssConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminOssProviderConfigResponse;
import org.springframework.core.env.ConfigurableEnvironment;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminOssConfigService {

    private static final String PROVIDER_ALIYUN = "aliyun";
    private static final String YAML_RESOURCE = "application.yaml";
    private static final String YAML_DISPLAY_PATH = "shopping-web/src/main/resources/application.yaml";
    private static final String ALIYUN_OSS_PATH = "aliyun.oss";
    private static final String ACCESS_KEY_ID_PROPERTY = ALIYUN_OSS_PATH + ".access-key-id";
    private static final String ACCESS_KEY_SECRET_PROPERTY = ALIYUN_OSS_PATH + ".access-key-secret";
    private static final String ACCESS_KEY_ID_ENV = "OSS_ACCESS_KEY_ID";
    private static final String ACCESS_KEY_SECRET_ENV = "OSS_ACCESS_KEY_SECRET";
    private static final String YAML_ACCESS_KEY_ID_KEY = "access-key-id";
    private static final String YAML_ACCESS_KEY_SECRET_KEY = "access-key-secret";
    private static final String NOT_CONFIGURED = "未配置";
    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");

    private final ConfigurableEnvironment environment;
    private final AdminWindowsSystemEnvService windowsSystemEnvService;
    private final Object monitor = new Object();

    public AdminOssConfigService(ConfigurableEnvironment environment,
                                 AdminWindowsSystemEnvService windowsSystemEnvService) {
        this.environment = environment;
        this.windowsSystemEnvService = windowsSystemEnvService;
    }

    public AdminOssProviderConfigResponse aliyunConfig() {
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata(ALIYUN_OSS_PATH);
        return new AdminOssProviderConfigResponse(
                PROVIDER_ALIYUN,
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
                windowsSystemEnvService.windowsEnvTarget(),
                true,
                true
        );
    }

    public AdminOssProviderConfigResponse updateAliyunConfig(AdminOssConfigUpdateRequest request) {
        String accessKeyId = normalizeUpdateValue(request == null ? null : request.accessKeyId());
        String accessKeySecret = normalizeUpdateValue(request == null ? null : request.accessKeySecret());
        if (accessKeyId == null && accessKeySecret == null) {
            throw new AdminServiceException(
                    "ADMIN_OSS_CONFIG_EMPTY",
                    "请至少填写一个需要修改的 OSS 配置值。",
                    HttpStatus.BAD_REQUEST
            );
        }
        synchronized (monitor) {
            if (accessKeyId != null) {
                writeWindowsSystemEnv(ACCESS_KEY_ID_ENV, accessKeyId);
            }
            if (accessKeySecret != null) {
                writeWindowsSystemEnv(ACCESS_KEY_SECRET_ENV, accessKeySecret);
            }
        }
        return aliyunConfig();
    }

    private AdminOAuth2ConfigField buildField(String propertyKey,
                                             String fallbackEnvName,
                                             AdminYamlFieldMetadata metadata) {
        String envName = metadata != null && StringUtils.hasText(metadata.envName())
                ? metadata.envName()
                : fallbackEnvName;
        String rawValue = windowsSystemEnvService.readSystemEnvValue(envName)
                .orElseGet(() -> readProperty(propertyKey));
        return new AdminOAuth2ConfigField(
                maskValue(rawValue),
                propertyKey,
                envName,
                windowsSystemEnvService.windowsEnvTarget(),
                YAML_DISPLAY_PATH,
                metadata == null ? null : metadata.yamlLine()
        );
    }

    private String readProperty(String propertyKey) {
        try {
            return environment.getProperty(propertyKey);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, AdminYamlFieldMetadata> yamlMetadata(String targetPath) {
        Resource resource = new ClassPathResource(YAML_RESOURCE);
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return parseYamlMetadata(reader.lines().toList(), targetPath);
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private Map<String, AdminYamlFieldMetadata> parseYamlMetadata(List<String> lines, String targetPath) {
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
            if (targetPath.equals(parentPath)
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

    private String maskValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return NOT_CONFIGURED;
        }
        String value = rawValue.trim();
        if (value.contains("${")) {
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

    private void writeWindowsSystemEnv(String envName, String value) {
        windowsSystemEnvService.writeWindowsSystemEnv(
                envName,
                value,
                "ADMIN_OSS_WINDOWS_ENV_UNSUPPORTED",
                "ADMIN_OSS_WINDOWS_ENV_WRITE_FAILED",
                "ADMIN_OSS_WINDOWS_ENV_WRITE_INTERRUPTED"
        );
    }

    private String normalizeUpdateValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new AdminServiceException(
                    "ADMIN_OSS_CONFIG_INVALID",
                    "OSS 配置值不能包含换行。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (trimmed.length() > 1024) {
            throw new AdminServiceException(
                    "ADMIN_OSS_CONFIG_TOO_LONG",
                    "OSS 配置值长度不能超过 1024 个字符。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return trimmed;
    }

    private record AdminYamlFieldMetadata(Integer yamlLine, String envName) {
    }
}
