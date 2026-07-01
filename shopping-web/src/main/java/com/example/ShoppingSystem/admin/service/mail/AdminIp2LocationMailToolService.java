package com.example.ShoppingSystem.admin.service.mail;

import com.example.ShoppingSystem.admin.dto.AdminIp2LocationMailBatchRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationRegistrationCheckResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationVerifyLinksResponse;
public interface AdminIp2LocationMailToolService {
    public AdminIp2LocationRegistrationCheckResponse checkRegistration(AdminIp2LocationMailBatchRequest request);

    public AdminIp2LocationVerifyLinksResponse readVerifyLinks(AdminIp2LocationMailBatchRequest request);
}
