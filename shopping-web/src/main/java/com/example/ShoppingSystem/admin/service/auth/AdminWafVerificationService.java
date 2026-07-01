package com.example.ShoppingSystem.admin.service.auth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
public interface AdminWafVerificationService {
    public static final String ADMIN_WAF_REQUIRED_ERROR_CODE = "ADMIN_IP_CHANGED_WAF_REQUIRED";

    public static final String ADMIN_WAF_REQUIRED_MESSAGE = "检测到管理员访问 IP 变化，请完成安全验证后重试";

    public ResponseCookie issueVerifiedCookie(HttpServletRequest request);

    public boolean consumeVerifiedTicket(HttpServletRequest request);

    public ResponseCookie buildClearVerifiedCookie(HttpServletRequest request);
}
