package com.example.ShoppingSystem.admin.service.config.impl.AdminManagedEnvService;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

import com.example.ShoppingSystem.admin.service.config.impl.AdminManagedEnvService.AdminManagedEnvServiceImpl;
import com.example.ShoppingSystem.admin.service.config.AdminManagedEnvService;
@Service
public class AdminManagedEnvServiceImpl implements AdminManagedEnvService {

    public static final String LINUX_ENV_FILE_ENV = "SHOPPING_ENV_FILE";
    public static final String DEFAULT_LINUX_ENV_FILE = "/etc/shopping/shopping.env";
    public static final String STORE_TYPE_WINDOWS_SYSTEM_ENV = "windows-system-env";
    public static final String STORE_TYPE_LINUX_SYSTEMD_ENV_FILE = "linux-systemd-env-file";

    private static final Pattern ENV_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern ENV_LINE_PATTERN = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=.*$");

    public String envTarget() {
        return envTarget(null);
    }

    public String windowsEnvTarget() {
        return envTarget();
    }

    public String envStoreType() {
        return envStoreType(null);
    }

    public Optional<String> readSystemEnvValue(String envName) {
        if (!isValidEnvName(envName)) {
            return Optional.empty();
        }
        if (isWindows()) {
            return AdminOAuth2WindowsEnvPostProcessor.readWindowsSystemEnvValue(envName);
        }
        return readLinuxEnvFileValues(linuxEnvFilePath(null)).getOptional(envName)
                .or(() -> Optional.ofNullable(System.getenv(envName)).filter(StringUtils::hasText));
    }

    public Map<String, String> readManagedEnvValues() {
        return readManagedEnvValues(AdminOAuth2WindowsEnvPostProcessor.MANAGED_ENV_NAMES, null);
    }

    public void writeSystemEnv(String envName,
                               String value,
                               String unsupportedCode,
                               String writeFailedCode,
                               String writeInterruptedCode) {
        if (!isValidEnvName(envName)) {
            throw new AdminServiceException(
                    writeFailedCode,
                    "Environment variable name is invalid.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (value == null || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new AdminServiceException(
                    writeFailedCode,
                    "Environment variable value must not contain line breaks.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (isWindows()) {
            writeWindowsSystemEnv(envName, value, writeFailedCode, writeInterruptedCode);
            return;
        }
        writeLinuxEnvFile(envName, value, writeFailedCode);
    }

    public void writeWindowsSystemEnv(String envName,
                                      String value,
                                      String unsupportedCode,
                                      String writeFailedCode,
                                      String writeInterruptedCode) {
        writeSystemEnv(envName, value, unsupportedCode, writeFailedCode, writeInterruptedCode);
    }

    public static Map<String, String> readManagedEnvValues(Collection<String> envNames,
                                                          ConfigurableEnvironment environment) {
        Map<String, String> values = new LinkedHashMap<>();
        if (envNames == null || envNames.isEmpty()) {
            return values;
        }
        if (isWindows()) {
            for (String envName : envNames) {
                if (isValidEnvName(envName)) {
                    AdminOAuth2WindowsEnvPostProcessor.readWindowsSystemEnvValue(envName)
                            .ifPresent(value -> values.put(envName, value));
                }
            }
            return values;
        }
        LinuxEnvValues envFileValues = readLinuxEnvFileValues(linuxEnvFilePath(environment));
        for (String envName : envNames) {
            if (!isValidEnvName(envName)) {
                continue;
            }
            envFileValues.getOptional(envName)
                    .or(() -> Optional.ofNullable(System.getenv(envName)).filter(StringUtils::hasText))
                    .ifPresent(value -> values.put(envName, value));
        }
        return values;
    }

    public static String envTarget(ConfigurableEnvironment environment) {
        if (isWindows()) {
            return AdminOAuth2WindowsEnvPostProcessor.WINDOWS_ENV_TARGET;
        }
        return linuxEnvFilePath(environment).toString();
    }

    public static String envStoreType(ConfigurableEnvironment environment) {
        return isWindows() ? STORE_TYPE_WINDOWS_SYSTEM_ENV : STORE_TYPE_LINUX_SYSTEMD_ENV_FILE;
    }

    public static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }

    private static boolean isValidEnvName(String envName) {
        return StringUtils.hasText(envName) && ENV_NAME_PATTERN.matcher(envName).matches();
    }

    private static Path linuxEnvFilePath(ConfigurableEnvironment environment) {
        String configuredPath = environment == null ? null : environment.getProperty(LINUX_ENV_FILE_ENV);
        if (!StringUtils.hasText(configuredPath)) {
            configuredPath = System.getProperty(LINUX_ENV_FILE_ENV);
        }
        if (!StringUtils.hasText(configuredPath)) {
            configuredPath = System.getenv(LINUX_ENV_FILE_ENV);
        }
        return Paths.get(StringUtils.hasText(configuredPath) ? configuredPath.trim() : DEFAULT_LINUX_ENV_FILE);
    }

    private static LinuxEnvValues readLinuxEnvFileValues(Path envFile) {
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return new LinuxEnvValues(Map.of());
        }
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                parseLinuxEnvLine(line).ifPresent(entry -> values.put(entry.name(), entry.value()));
            }
            return new LinuxEnvValues(values);
        } catch (IOException ex) {
            return new LinuxEnvValues(Map.of());
        }
    }

    private static Optional<LinuxEnvEntry> parseLinuxEnvLine(String line) {
        if (!StringUtils.hasText(line)) {
            return Optional.empty();
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String content = trimmed.startsWith("export ") ? trimmed.substring("export ".length()).trim() : trimmed;
        int delimiter = content.indexOf('=');
        if (delimiter <= 0) {
            return Optional.empty();
        }
        String name = content.substring(0, delimiter).trim();
        if (!isValidEnvName(name)) {
            return Optional.empty();
        }
        String value = content.substring(delimiter + 1).trim();
        return Optional.of(new LinuxEnvEntry(name, decodeLinuxEnvValue(value)));
    }

    private void writeWindowsSystemEnv(String envName,
                                       String value,
                                       String writeFailedCode,
                                       String writeInterruptedCode) {
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
                        writeFailedCode,
                        "Writing Windows system environment variable failed: " + output.trim(),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (IOException ex) {
            throw new AdminServiceException(
                    writeFailedCode,
                    "Writing Windows system environment variable failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AdminServiceException(
                    writeInterruptedCode,
                    "Writing Windows system environment variable was interrupted.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void writeLinuxEnvFile(String envName, String value, String writeFailedCode) {
        Path envFile = linuxEnvFilePath(null);
        try {
            Path parent = envFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> existingLines = Files.isRegularFile(envFile)
                    ? Files.readAllLines(envFile, StandardCharsets.UTF_8)
                    : List.of();
            List<String> nextLines = updateEnvLines(existingLines, envName, value);
            Path tempFile = parent == null
                    ? Files.createTempFile("shopping-env-", ".tmp")
                    : Files.createTempFile(parent, "shopping-env-", ".tmp");
            Files.write(
                    tempFile,
                    nextLines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            setOwnerOnlyPermissions(tempFile);
            try {
                Files.move(tempFile, envFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, envFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            throw new AdminServiceException(
                    writeFailedCode,
                    "Writing Linux systemd environment file failed: " + envFile,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private static List<String> updateEnvLines(List<String> lines, String envName, String value) {
        List<String> nextLines = new ArrayList<>();
        boolean found = false;
        String replacement = envName + "=" + encodeLinuxEnvValue(value);
        for (String line : lines) {
            Matcher matcher = ENV_LINE_PATTERN.matcher(line);
            if (matcher.matches() && envName.equals(matcher.group(1))) {
                nextLines.add(replacement);
                found = true;
            } else {
                nextLines.add(line);
            }
        }
        if (!found) {
            nextLines.add(replacement);
        }
        return nextLines;
    }

    private static String encodeLinuxEnvValue(String value) {
        String safeValue = value == null ? "" : value;
        return "\"" + safeValue
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private static String decodeLinuxEnvValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return unescapeDoubleQuoted(trimmed.substring(1, trimmed.length() - 1));
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String unescapeDoubleQuoted(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (escaped) {
                result.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else {
                result.append(current);
            }
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }

    private static void setOwnerOnlyPermissions(Path path) {
        Set<PosixFilePermission> permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
        );
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows and some filesystems do not expose POSIX permissions.
        }
    }

    private record LinuxEnvEntry(String name, String value) {
    }

    private record LinuxEnvValues(Map<String, String> values) {

        Optional<String> getOptional(String name) {
            return Optional.ofNullable(values.get(name)).filter(StringUtils::hasText);
        }
    }
}
