package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class AdminWindowsSystemEnvService {

    private static final Pattern ENV_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public String windowsEnvTarget() {
        return AdminOAuth2WindowsEnvPostProcessor.WINDOWS_ENV_TARGET;
    }

    public Optional<String> readSystemEnvValue(String envName) {
        if (!StringUtils.hasText(envName) || !ENV_NAME_PATTERN.matcher(envName).matches()) {
            return Optional.empty();
        }
        if (AdminOAuth2WindowsEnvPostProcessor.isWindows()) {
            return AdminOAuth2WindowsEnvPostProcessor.readWindowsSystemEnvValue(envName);
        }
        return Optional.ofNullable(System.getenv(envName))
                .filter(StringUtils::hasText);
    }

    public void writeWindowsSystemEnv(String envName,
                                      String value,
                                      String unsupportedCode,
                                      String writeFailedCode,
                                      String writeInterruptedCode) {
        if (!StringUtils.hasText(envName) || !ENV_NAME_PATTERN.matcher(envName).matches()) {
            throw new AdminServiceException(
                    writeFailedCode,
                    "环境变量名称不合法。",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!AdminOAuth2WindowsEnvPostProcessor.isWindows()) {
            throw new AdminServiceException(
                    unsupportedCode,
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
                        writeFailedCode,
                        "写入 Windows 系统环境变量失败，请确认 Spring Boot 以管理员身份运行：" + output.trim(),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (IOException ex) {
            throw new AdminServiceException(
                    writeFailedCode,
                    "写入 Windows 系统环境变量失败，请确认 Spring Boot 以管理员身份运行。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AdminServiceException(
                    writeInterruptedCode,
                    "写入 Windows 系统环境变量被中断。",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
