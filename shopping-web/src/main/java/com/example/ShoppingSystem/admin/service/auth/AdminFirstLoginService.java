package com.example.ShoppingSystem.admin.service.auth;

import com.example.ShoppingSystem.admin.dto.AdminEmailCodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminFirstLoginCompleteRequest;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.admin.service.config.AdminConfigService;

public interface AdminFirstLoginService {
    public AdminEmailCodeResponse sendEmailCode(String email);

    public void complete(AdminFirstLoginCompleteRequest request);
}
