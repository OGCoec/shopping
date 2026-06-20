package com.example.ShoppingSystem.admin.service.config.impl.AdminWindowsSystemEnvService;

import org.springframework.stereotype.Service;

import java.util.Optional;

import com.example.ShoppingSystem.admin.service.config.AdminWindowsSystemEnvService;
import com.example.ShoppingSystem.admin.service.config.AdminManagedEnvService;
@Deprecated
@Service
public class AdminWindowsSystemEnvServiceImpl implements AdminWindowsSystemEnvService {

    private final AdminManagedEnvService managedEnvService;

    public AdminWindowsSystemEnvServiceImpl(AdminManagedEnvService managedEnvService) {
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
