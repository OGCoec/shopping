package com.example.ShoppingSystem.admin.service.config;

import java.util.Optional;

public interface AdminWindowsSystemEnvService {
    public String windowsEnvTarget();

    public Optional<String> readSystemEnvValue(String envName);

    public void writeWindowsSystemEnv(String envName,
                                      String value,
                                      String unsupportedCode,
                                      String writeFailedCode,
                                      String writeInterruptedCode);
}
