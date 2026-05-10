package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import com.example.ShoppingSystem.admin.dto.AdminSmtpConfigField;
import com.example.ShoppingSystem.admin.dto.AdminSmtpConfigUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProviderConfigResponse;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProviderSummary;
import com.example.ShoppingSystem.admin.dto.AdminSmtpProvidersResponse;
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
public class AdminSmtpConfigService {

    private static final String PROVIDER_QQ = "qq";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_MICROSOFT = "microsoft";
    private static final String PROVIDER_CUSTOM = "custom";
    private static final String YAML_RESOURCE = "application.yaml";
    private static final String YAML_DISPLAY_PATH = "shopping-web/src/main/resources/application.yaml";
    private static final String MAIL_HOST_PROPERTY = "spring.mail.host";
    private static final String MAIL_PORT_PROPERTY = "spring.mail.port";
    private static final String MAIL_USERNAME_PROPERTY = "spring.mail.username";
    private static final String MAIL_PASSWORD_PROPERTY = "spring.mail.password";
    private static final String EMAIL_SMTP_USERNAME_ENV = "EMAIL_SMTP_USERNAME";
    private static final String EMAIL_SMTP_PASSWORD_ENV = "EMAIL_SMTP_PASSWORD";
    private static final String WINDOWS_ENV_PROPERTY_SOURCE = AdminOAuth2WindowsEnvPostProcessor.PROPERTY_SOURCE_NAME;
    private static final String WINDOWS_ENV_TARGET = AdminOAuth2WindowsEnvPostProcessor.WINDOWS_ENV_TARGET;
    private static final String NOT_CONFIGURED = "未配置";
    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<ProviderDefinition> PROVIDERS = List.of(
            new ProviderDefinition(PROVIDER_QQ, "QQ 邮箱", "QQ 邮箱 SMTP 验证码与通知发送", "smtp.qq.com", "587"),
            new ProviderDefinition(PROVIDER_GOOGLE, "Google / Gmail", "Gmail SMTP 邮件发送", "smtp.gmail.com", "587"),
            new ProviderDefinition(PROVIDER_MICROSOFT, "Microsoft / Outlook", "Microsoft 365 / Outlook SMTP 邮件发送", "smtp.office365.com", "587"),
            new ProviderDefinition(PROVIDER_CUSTOM, "自定义 SMTP", "使用当前 spring.mail.host 自定义 SMTP 服务", "-", "-")
    );

    private final ConfigurableEnvironment environment;
    private final Map<String, Object> windowsEnvValues = new ConcurrentHashMap<>();
    private final Object monitor = new Object();

    public AdminSmtpConfigService(ConfigurableEnvironment environment) {
        this.environment = environment;
        loadWindowsEnvPropertySource();
    }

    public AdminSmtpProvidersResponse providers() {
        ProviderDefinition currentDefinition = currentProviderDefinition();
        List<AdminSmtpProviderSummary> providers = List.of(new AdminSmtpProviderSummary(
                currentDefinition.provider(),
                currentDefinition.displayName(),
                currentDefinition.description(),
                true
        ));
        return new AdminSmtpProvidersResponse(
                providers,
                currentDefinition.provider(),
                currentDefinition.displayName()
        );
    }

    public AdminSmtpProviderConfigResponse providerConfig(String provider) {
        ProviderDefinition definition = providerDefinition(provider);
        ProviderDefinition currentDefinition = currentProviderDefinition();
        Map<String, AdminYamlFieldMetadata> yamlMetadata = yamlMetadata();
        List<AdminSmtpConfigField> fields = new ArrayList<>();
        boolean current = definition.provider().equals(currentDefinition.provider());
        fields.add(runtimeField("host", MAIL_HOST_PROPERTY, null, yamlMetadata, DisplayMode.PLAIN, false));
        fields.add(runtimeField("port", MAIL_PORT_PROPERTY, null, yamlMetadata, DisplayMode.PLAIN, false));
        fields.add(runtimeField("username", MAIL_USERNAME_PROPERTY, EMAIL_SMTP_USERNAME_ENV, yamlMetadata, DisplayMode.EMAIL, true));
        fields.add(runtimeField("password", MAIL_PASSWORD_PROPERTY, EMAIL_SMTP_PASSWORD_ENV, yamlMetadata, DisplayMode.PASSWORD, true));
        return new AdminSmtpProviderConfigResponse(
                definition.provider(),
                definition.displayName(),
                definition.description(),
                current,
                currentDefinition.provider(),
                currentDefinition.displayName(),
                WINDOWS_ENV_TARGET,
                true,
                fields
        );
    }

    public AdminSmtpProviderConfigResponse updateConfig(String provider,
                                                        AdminSmtpConfigUpdateRequest request) {
        ProviderDefinition definition = providerDefinition(provider);
        if (!PROVIDER_QQ.equals(definition.provider())) {
            throw new AdminServiceException(
                    "ADMIN_SMTP_PROVIDER_UNSUPPORTED",
                    "当前只支持修改 QQ 邮箱 SMTP 配置。",
                    HttpStatus.BAD_REQUEST
            );
        }
        String username = normalizeUpdateValue(request == null ? null : request.username(), "username");
        String password = normalizeUpdateValue(request == null ? null : request.password(), "password");
        if (username == null && password == null) {
            throw new AdminServiceException(
                    "ADMIN_SMTP_CONFIG_EMPTY",
                    "请至少填写一个需要修改的 SMTP 配置值。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (username != null && !EMAIL_PATTERN.matcher(username).matches()) {
            throw new AdminServiceException(
                    "ADMIN_SMTP_USERNAME_INVALID",
                    "SMTP 用户名必须是有效邮箱地址。",
                    HttpStatus.BAD_REQUEST
            );
        }
        synchronized (monitor) {
            if (username != null) {
                writeWindowsSystemEnv(EMAIL_SMTP_USERNAME_ENV, username);
                windowsEnvValues.put(EMAIL_SMTP_USERNAME_ENV, username);
            }
            if (password != null) {
                writeWindowsSystemEnv(EMAIL_SMTP_PASSWORD_ENV, password);
                windowsEnvValues.put(EMAIL_SMTP_PASSWORD_ENV, password);
            }
            refreshWindowsEnvPropertySource();
        }
        return providerConfig(definition.provider());
    }

    private AdminSmtpConfigField runtimeField(String label,
                                             String propertyKey,
                                             String fallbackEnvName,
                                             Map<String, AdminYamlFieldMetadata> yamlMetadata,
                                             DisplayMode displayMode,
                                             boolean sensitive) {
        return configField(
                label,
                formatValue(readProperty(propertyKey), displayMode),
                propertyKey,
                fallbackEnvName,
                yamlMetadata,
                sensitive
        );
    }

    private AdminSmtpConfigField configField(String label,
                                            String maskedValue,
                                            String propertyKey,
                                            String fallbackEnvName,
                                            Map<String, AdminYamlFieldMetadata> yamlMetadata,
                                            boolean sensitive) {
        AdminYamlFieldMetadata metadata = propertyKey == null ? null : yamlMetadata.get(propertyKey);
        String envName = metadata != null && StringUtils.hasText(metadata.envName())
                ? metadata.envName()
                : fallbackEnvName;
        return new AdminSmtpConfigField(
                label,
                StringUtils.hasText(maskedValue) ? maskedValue : NOT_CONFIGURED,
                propertyKey,
                envName,
                envName == null ? null : WINDOWS_ENV_TARGET,
                metadata == null ? null : YAML_DISPLAY_PATH,
                metadata == null ? null : metadata.yamlLine(),
                sensitive
        );
    }

    private String formatValue(String value, DisplayMode displayMode) {
        if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
            return NOT_CONFIGURED;
        }
        return switch (displayMode) {
            case EMAIL -> maskEmail(value);
            case PASSWORD -> maskSecret(value);
            case PLAIN -> value;
        };
    }

    private String readProperty(String propertyKey) {
        try {
            return environment.getProperty(propertyKey);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ProviderDefinition currentProviderDefinition() {
        String host = readProperty(MAIL_HOST_PROPERTY);
        if (!StringUtils.hasText(host)) {
            return providerDefinition(PROVIDER_CUSTOM);
        }
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        if ("smtp.qq.com".equals(normalizedHost)) {
            return providerDefinition(PROVIDER_QQ);
        }
        if ("smtp.gmail.com".equals(normalizedHost)) {
            return providerDefinition(PROVIDER_GOOGLE);
        }
        if ("smtp.office365.com".equals(normalizedHost) || "smtp-mail.outlook.com".equals(normalizedHost)) {
            return providerDefinition(PROVIDER_MICROSOFT);
        }
        return providerDefinition(PROVIDER_CUSTOM);
    }

    private ProviderDefinition providerDefinition(String provider) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        return PROVIDERS.stream()
                .filter(definition -> definition.provider().equals(normalizedProvider))
                .findFirst()
                .orElseThrow(() -> new AdminServiceException(
                        "ADMIN_SMTP_PROVIDER_UNSUPPORTED",
                        "不支持的 SMTP 服务。",
                        HttpStatus.BAD_REQUEST
                ));
    }

    private String maskEmail(String value) {
        if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
            return NOT_CONFIGURED;
        }
        String trimmed = value.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex > 0) {
            String local = trimmed.substring(0, atIndex);
            String domain = trimmed.substring(atIndex);
            if (local.length() <= 2) {
                return local.charAt(0) + "****" + domain;
            }
            int prefixLength = Math.min(3, local.length());
            int suffixLength = local.length() > 5 ? 2 : 0;
            String prefix = local.substring(0, prefixLength);
            String suffix = suffixLength == 0 ? "" : local.substring(local.length() - suffixLength);
            return prefix + "****" + suffix + domain;
        }
        if (trimmed.length() <= 4) {
            return trimmed.charAt(0) + "****";
        }
        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private String maskSecret(String value) {
        if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
            return NOT_CONFIGURED;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        if (trimmed.length() <= 8) {
            return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private boolean isUnresolvedPlaceholder(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}");
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
                    "ADMIN_SMTP_WINDOWS_ENV_UNSUPPORTED",
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
                        "ADMIN_SMTP_WINDOWS_ENV_WRITE_FAILED",
                        "写入 Windows 系统环境变量失败，请确认 Spring Boot 以管理员身份运行：" + output.trim(),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (IOException ex) {
            throw new AdminServiceException(
                    "ADMIN_SMTP_WINDOWS_ENV_WRITE_FAILED",
                    "写入 Windows 系统环境变量失败。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AdminServiceException(
                    "ADMIN_SMTP_WINDOWS_ENV_WRITE_INTERRUPTED",
                    "写入 Windows 系统环境变量被中断。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String normalizeUpdateValue(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new AdminServiceException(
                    "ADMIN_SMTP_CONFIG_INVALID",
                    "SMTP " + fieldName + " 不能包含换行。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (trimmed.length() > 1024) {
            throw new AdminServiceException(
                    "ADMIN_SMTP_CONFIG_TOO_LONG",
                    "SMTP " + fieldName + " 长度不能超过 1024 个字符。",
                    HttpStatus.BAD_REQUEST
            );
        }
        return trimmed;
    }

    private enum DisplayMode {
        PLAIN,
        EMAIL,
        PASSWORD
    }

    private record ProviderDefinition(String provider,
                                      String displayName,
                                      String description,
                                      String defaultHost,
                                      String defaultPort) {

        String defaultEndpoint() {
            if ("-".equals(defaultHost)) {
                return "使用 spring.mail.host / spring.mail.port";
            }
            return defaultHost + ":" + defaultPort;
        }
    }

    private record AdminYamlFieldMetadata(Integer yamlLine, String envName) {
    }
}
