package com.example.ShoppingSystem.admin.service.config;
import java.util.Map;
import java.util.Optional;
public interface AdminManagedEnvService {
    public static final String LINUX_ENV_FILE_ENV = "SHOPPING_ENV_FILE";

    public static final String DEFAULT_LINUX_ENV_FILE = "/etc/shopping/shopping.env";

    public static final String STORE_TYPE_WINDOWS_SYSTEM_ENV = "windows-system-env";

    public static final String STORE_TYPE_LINUX_SYSTEMD_ENV_FILE = "linux-systemd-env-file";

    public String envTarget();

    public String windowsEnvTarget();

    public String envStoreType();

    public Optional<String> readSystemEnvValue(String envName);

    public Map<String, String> readManagedEnvValues();

    public void writeSystemEnv(String envName,
                               String value,
                               String unsupportedCode,
                               String writeFailedCode,
                               String writeInterruptedCode);

    public void writeWindowsSystemEnv(String envName,
                                      String value,
                                      String unsupportedCode,
                                      String writeFailedCode,
                                      String writeInterruptedCode);
}
