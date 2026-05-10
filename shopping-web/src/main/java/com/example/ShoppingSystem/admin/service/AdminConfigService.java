package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.config.AdminYamlProperties;
import com.example.ShoppingSystem.admin.mapper.AdminYamlConfigMapper;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class AdminConfigService {

    private final AdminYamlProperties properties;
    private final AdminYamlConfigMapper adminYamlConfigMapper;
    private final Object monitor = new Object();

    public AdminConfigService(AdminYamlProperties properties,
                              AdminYamlConfigMapper adminYamlConfigMapper) {
        this.properties = properties;
        this.adminYamlConfigMapper = adminYamlConfigMapper;
    }

    public AdminAccount readAccount() {
        synchronized (monitor) {
            return adminYamlConfigMapper.read(configPath());
        }
    }

    public boolean isInitialized() {
        AdminAccount account = readAccount();
        return account.isInitialized();
    }

    public AdminAccount requireInitialized() {
        AdminAccount account = readAccount();
        if (!account.isInitialized()) {
            throw new AdminServiceException(
                    "ADMIN_NOT_INITIALIZED",
                    "管理员账号尚未初始化。",
                    HttpStatus.CONFLICT
            );
        }
        return account;
    }

    public AdminAccount initialize(AdminAccount account) {
        synchronized (monitor) {
            AdminAccount existing = adminYamlConfigMapper.read(configPath());
            if (existing.isInitialized()) {
                throw new AdminServiceException(
                        "ADMIN_ALREADY_INITIALIZED",
                        "管理员账号已经初始化。",
                        HttpStatus.CONFLICT
                );
            }
            account.setInitialized(true);
            adminYamlConfigMapper.write(configPath(), account);
            return account;
        }
    }

    private Path configPath() {
        Path configured = Paths.get(properties.getPath());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (userDir.getFileName() != null && "shopping-web".equals(userDir.getFileName().toString())) {
            Path parent = userDir.getParent();
            if (parent != null) {
                return parent.resolve(configured).normalize();
            }
        }
        return userDir.resolve(configured).normalize();
    }
}
