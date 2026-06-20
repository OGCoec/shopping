package com.example.ShoppingSystem.admin.service.mail;

import com.example.ShoppingSystem.admin.dto.AdminIp2LocationMailBatchRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationRegistrationCheckItem;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationRegistrationCheckResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationVerifyLinkItem;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationVerifyLinksResponse;
import com.example.ShoppingSystem.tools.ip2location.verify.Ip2LocationVerifyMailReaderService;
import com.example.ShoppingSystem.tools.ip2location.verify.model.MailCredentials;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

public interface AdminIp2LocationMailToolService {
    public AdminIp2LocationRegistrationCheckResponse checkRegistration(AdminIp2LocationMailBatchRequest request);

    public AdminIp2LocationVerifyLinksResponse readVerifyLinks(AdminIp2LocationMailBatchRequest request);
}
