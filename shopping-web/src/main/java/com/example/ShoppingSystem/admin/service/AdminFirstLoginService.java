package com.example.ShoppingSystem.admin.service;

import com.example.ShoppingSystem.admin.dto.AdminEmailCodeResponse;
import com.example.ShoppingSystem.admin.dto.AdminFirstLoginCompleteRequest;
import com.example.ShoppingSystem.admin.model.AdminAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

@Service
public class AdminFirstLoginService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{6,20}$");
    private static final Pattern EMAIL_CODE_PATTERN = Pattern.compile("^\\d{6}$");

    private final AdminConfigService adminConfigService;
    private final AdminEmailCodeService adminEmailCodeService;
    private final PasswordEncoder passwordEncoder;

    public AdminFirstLoginService(AdminConfigService adminConfigService,
                                  AdminEmailCodeService adminEmailCodeService,
                                  PasswordEncoder passwordEncoder) {
        this.adminConfigService = adminConfigService;
        this.adminEmailCodeService = adminEmailCodeService;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminEmailCodeResponse sendEmailCode(String email) {
        ensureNotInitialized();
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        adminEmailCodeService.sendFirstLoginEmailCode(normalizedEmail);
        return new AdminEmailCodeResponse(
                adminEmailCodeService.ttlSeconds(),
                adminEmailCodeService.cooldownSeconds()
        );
    }

    public void complete(AdminFirstLoginCompleteRequest request) {
        ensureNotInitialized();
        String username = normalizeText(request == null ? null : request.username());
        String email = normalizeEmail(request == null ? null : request.email());
        String phone = normalizePhone(request == null ? null : request.phone());
        String password = request == null ? "" : blankToEmpty(request.password());
        String emailCode = normalizeText(request == null ? null : request.emailCode());

        validateUsername(username);
        validateEmail(email);
        validatePhone(phone);
        validatePassword(password);
        validateEmailCodeFormat(emailCode);

        adminEmailCodeService.verifyAndClear(email, emailCode);

        AdminAccount account = AdminAccount.empty();
        account.setUsername(username);
        account.setEmail(email);
        account.setPhone(phone);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setUpdatedAt(OffsetDateTime.now().toString());
        adminConfigService.initialize(account);
    }

    private void ensureNotInitialized() {
        if (adminConfigService.isInitialized()) {
            throw new AdminServiceException(
                    "ADMIN_ALREADY_INITIALIZED",
                    "管理员账号已经初始化。",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.length() < 2 || username.length() > 64) {
            throw new AdminServiceException(
                    "ADMIN_USERNAME_INVALID",
                    "管理员用户名长度需要在 2 到 64 个字符之间。",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new AdminServiceException(
                    "ADMIN_EMAIL_INVALID",
                    "请输入有效的邮箱地址。",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validatePhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new AdminServiceException(
                    "ADMIN_PHONE_INVALID",
                    "请输入有效的手机号。",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new AdminServiceException(
                    "ADMIN_PASSWORD_INVALID",
                    "管理员密码长度需要在 8 到 128 个字符之间。",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateEmailCodeFormat(String emailCode) {
        if (emailCode == null || !EMAIL_CODE_PATTERN.matcher(emailCode).matches()) {
            throw new AdminServiceException(
                    "ADMIN_EMAIL_CODE_INVALID",
                    "请输入 6 位数字邮箱验证码。",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String normalizeEmail(String email) {
        return normalizeText(email) == null ? "" : normalizeText(email).toLowerCase();
    }

    private String normalizePhone(String phone) {
        return normalizeText(phone) == null ? "" : normalizeText(phone).replace(" ", "");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
