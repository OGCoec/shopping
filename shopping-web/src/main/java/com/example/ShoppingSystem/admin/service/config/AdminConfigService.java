package com.example.ShoppingSystem.admin.service.config;

import com.example.ShoppingSystem.admin.config.AdminYamlProperties;
import com.example.ShoppingSystem.admin.mapper.AdminYamlConfigMapper;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import org.springframework.http.HttpStatus;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminConfigService {
    public AdminAccount readAccount();

    public boolean isInitialized();

    public AdminAccount requireInitialized();

    public AdminAccount initialize(AdminAccount account);
}
