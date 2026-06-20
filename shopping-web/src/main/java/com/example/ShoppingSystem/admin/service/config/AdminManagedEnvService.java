package com.example.ShoppingSystem.admin.service.config;

import com.example.ShoppingSystem.admin.config.AdminOAuth2WindowsEnvPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpStatus;
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
