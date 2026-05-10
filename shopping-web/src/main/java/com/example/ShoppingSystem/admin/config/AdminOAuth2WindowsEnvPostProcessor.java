package com.example.ShoppingSystem.admin.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminOAuth2WindowsEnvPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "adminOAuth2WindowsEnv";
    public static final String WINDOWS_ENV_TARGET = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";
    public static final List<String> OAUTH2_ENV_NAMES = List.of(
            "GITHUB_CLIENT_ID",
            "GITHUB_CLIENT_SECRET",
            "GOOGLE_CLIENT_ID",
            "GOOGLE_CLIENT_SECRET",
            "AZURE_CLIENT_ID",
            "AZURE_CLIENT_SECRET"
    );
    public static final List<String> OSS_ENV_NAMES = List.of(
            "OSS_ACCESS_KEY_ID",
            "OSS_ACCESS_KEY_SECRET",
            "OSS_SESSION_TOKEN"
    );
    public static final List<String> SMS_ENV_NAMES = List.of(
            "ALIBABA_CLOUD_ACCESS_KEY_ID",
            "ALIBABA_CLOUD_ACCESS_KEY_SECRET"
    );
    public static final List<String> CAPTCHA_ENV_NAMES = List.of(
            "TURNSTILE_SITE_KEY",
            "TURNSTILE_SECRET_KEY",
            "HCAPTCHA_SITE_KEY",
            "HCAPTCHA_SECRET_KEY",
            "RECAPTCHA_SITE_KEY",
            "RECAPTCHA_SECRET_KEY"
    );
    public static final List<String> RISK_API_ENV_NAMES = List.of(
            "IP2LOCATION_IO_API_URL",
            "IPING_API_ENABLED",
            "IPING_API_URL",
            "IPING_API_LANGUAGE"
    );
    public static final List<String> SMTP_ENV_NAMES = List.of(
            "EMAIL_SMTP_USERNAME",
            "EMAIL_SMTP_PASSWORD"
    );
    public static final List<String> MANAGED_ENV_NAMES = List.of(
            "GITHUB_CLIENT_ID",
            "GITHUB_CLIENT_SECRET",
            "GOOGLE_CLIENT_ID",
            "GOOGLE_CLIENT_SECRET",
            "AZURE_CLIENT_ID",
            "AZURE_CLIENT_SECRET",
            "OSS_ACCESS_KEY_ID",
            "OSS_ACCESS_KEY_SECRET",
            "OSS_SESSION_TOKEN",
            "ALIBABA_CLOUD_ACCESS_KEY_ID",
            "ALIBABA_CLOUD_ACCESS_KEY_SECRET",
            "TURNSTILE_SITE_KEY",
            "TURNSTILE_SECRET_KEY",
            "HCAPTCHA_SITE_KEY",
            "HCAPTCHA_SECRET_KEY",
            "RECAPTCHA_SITE_KEY",
            "RECAPTCHA_SECRET_KEY",
            "IP2LOCATION_IO_API_URL",
            "IPING_API_ENABLED",
            "IPING_API_URL",
            "IPING_API_LANGUAGE",
            "EMAIL_SMTP_USERNAME",
            "EMAIL_SMTP_PASSWORD"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isWindows()) {
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String envName : MANAGED_ENV_NAMES) {
            readWindowsSystemEnvValue(envName).ifPresent(value -> values.put(envName, value));
        }
        if (!values.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    public static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }

    public static Optional<String> readWindowsSystemEnvValue(String envName) {
        try {
            Process process = new ProcessBuilder(
                    windowsTool("reg.exe"),
                    "query",
                    WINDOWS_ENV_TARGET,
                    "/v",
                    envName
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return Optional.empty();
            }
            return Optional.ofNullable(parseWindowsSystemEnvValue(envName, output));
        } catch (IOException ex) {
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String parseWindowsSystemEnvValue(String envName, String output) {
        if (!StringUtils.hasText(output)) {
            return null;
        }
        Pattern pattern = Pattern.compile(
                "^\\s*" + Pattern.quote(envName) + "\\s+REG_(?:EXPAND_)?SZ\\s*(.*)$",
                Pattern.CASE_INSENSITIVE
        );
        String[] lines = output.split("\\R");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    public static String windowsTool(String executable) {
        String systemRoot = System.getenv("SystemRoot");
        if (StringUtils.hasText(systemRoot)) {
            Path path = Paths.get(systemRoot, "System32", executable);
            if (Files.exists(path)) {
                return path.toString();
            }
        }
        return executable;
    }
}
