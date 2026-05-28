package com.example.ShoppingSystem.admin.service.config;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Deprecated
@Service
public class AdminWindowsSystemEnvService {

    private final AdminManagedEnvService managedEnvService;

    public AdminWindowsSystemEnvService(AdminManagedEnvService managedEnvService) {
        this.managedEnvService = managedEnvService;
    }

    public String windowsEnvTarget() {
        return managedEnvService.envTarget();
    }

    public Optional<String> readSystemEnvValue(String envName) {
        return managedEnvService.readSystemEnvValue(envName);
    }

    public void writeWindowsSystemEnv(String envName,
                                      String value,
                                      String unsupportedCode,
                                      String writeFailedCode,
                                      String writeInterruptedCode) {
        managedEnvService.writeSystemEnv(
                envName,
                value,
                unsupportedCode,
                writeFailedCode,
                writeInterruptedCode
        );
    }
}
