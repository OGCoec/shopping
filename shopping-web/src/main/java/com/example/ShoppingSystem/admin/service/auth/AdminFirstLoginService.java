package com.example.ShoppingSystem.admin.service.auth;

import com.example.ShoppingSystem.admin.dto.AdminEmailCodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminFirstLoginCompleteRequest;
public interface AdminFirstLoginService {
    public AdminEmailCodeResponse sendEmailCode(String email);

    public void complete(AdminFirstLoginCompleteRequest request);
}
