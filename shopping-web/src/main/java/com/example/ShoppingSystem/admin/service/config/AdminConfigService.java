package com.example.ShoppingSystem.admin.service.config;
import com.example.ShoppingSystem.admin.model.AdminAccount;
public interface AdminConfigService {
    public AdminAccount readAccount();

    public boolean isInitialized();

    public AdminAccount requireInitialized();

    public AdminAccount initialize(AdminAccount account);
}
