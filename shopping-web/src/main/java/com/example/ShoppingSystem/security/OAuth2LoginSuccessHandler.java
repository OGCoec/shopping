package com.example.ShoppingSystem.security;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.service.user.auth.login.GithubAuthService;
import com.example.ShoppingSystem.service.user.auth.login.GoogleAuthService;
import com.example.ShoppingSystem.service.user.auth.login.MicrosoftAuthService;
import com.example.ShoppingSystem.service.user.auth.login.UserProfileService;
import com.example.ShoppingSystem.service.user.auth.login.model.UserProfileDraft;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * OAuth2 登录成功处理器。
 * 负责根据 provider 完成本地身份绑定，并回跳 login.html。
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String LOGIN_PAGE_URL = "https://localhost:6655/shopping/user/login";

    private final GithubAuthService githubAuthService;
    private final GoogleAuthService googleAuthService;
    private final MicrosoftAuthService microsoftAuthService;
    private final UserProfileService userProfileService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate = new RestTemplate();

    public OAuth2LoginSuccessHandler(GithubAuthService githubAuthService,
                                     GoogleAuthService googleAuthService,
                                     MicrosoftAuthService microsoftAuthService,
                                     UserProfileService userProfileService,
                                     OAuth2AuthorizedClientService authorizedClientService) {
        this.githubAuthService = githubAuthService;
        this.googleAuthService = googleAuthService;
        this.microsoftAuthService = microsoftAuthService;
        this.userProfileService = userProfileService;
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();
        Map<String, Object> attrs = token.getPrincipal().getAttributes();

        try {
            UserLoginIdentity identity = switch (registrationId) {
                case "github" -> githubAuthService.loginByGithub(
                        stringValue(attrs.get("id")),
                        normalizeEmail(stringValue(attrs.get("email")))
                );
                case "google" -> googleAuthService.loginByGoogle(
                        stringValue(attrs.get("sub")),
                        normalizeEmail(stringValue(attrs.get("email")))
                );
                case "microsoft" -> {
                    String microsoftId = firstNonBlank(
                            stringValue(attrs.get("id")),
                            stringValue(attrs.get("oid")),
                            stringValue(attrs.get("sub"))
                    );
                    String email = normalizeEmail(stringValue(attrs.get("email")));
                    if (email == null) {
                        email = fetchMicrosoftEmailFromGraph(token);
                    }
                    yield microsoftAuthService.loginByMicrosoft(microsoftId, email);
                }
                default -> null;
            };

            if (identity == null) {
                response.sendRedirect(buildFailedUrl(registrationId, "bind_failed"));
                return;
            }

            try {
                UserProfileDraft profileDraft = buildProfileDraft(registrationId, attrs);
                userProfileService.initIfAbsent(identity.getUserId(), profileDraft);
            } catch (Exception profileEx) {
                // 资料初始化失败不影响主登录流程
                profileEx.printStackTrace();
            }

            response.sendRedirect(LOGIN_PAGE_URL + "?" + registrationId + "=success");
        } catch (Exception ex) {
            response.sendRedirect(buildFailedUrl(registrationId, "sys_error"));
        }
    }

    /**
     * 当 Microsoft claim 缺少邮箱时，调用 Graph /me 兜底获取邮箱。
     */
    private String fetchMicrosoftEmailFromGraph(OAuth2AuthenticationToken token) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName()
        );
        if (client == null || client.getAccessToken() == null) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(client.getAccessToken().getTokenValue());
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://graph.microsoft.com/v1.0/me?$select=id,mail,userPrincipalName",
                HttpMethod.GET,
                requestEntity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }

        Object mail = response.getBody().get("mail");
        if (mail instanceof String m && !m.isBlank()) {
            return normalizeEmail(m);
        }

        Object upn = response.getBody().get("userPrincipalName");
        if (upn instanceof String u && !u.isBlank()) {
            return normalizeEmail(u);
        }

        return null;
    }

    private String buildFailedUrl(String registrationId, String reason) {
        String rid = (registrationId == null || registrationId.isBlank()) ? "oauth" : registrationId;
        String msg = URLEncoder.encode(reason, StandardCharsets.UTF_8);
        return LOGIN_PAGE_URL + "?" + rid + "=failed&msg=" + msg;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String attrValue(Map<String, Object> attrs, String key) {
        return stringValue(attrs.get(key));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 从 OAuth Provider 属性构建首次登录的用户资料草稿。
     * 映射原则：尽量提取原始字段，拿不到就置为 null。
     */
    private UserProfileDraft buildProfileDraft(String registrationId, Map<String, Object> attrs) {
        String gender = firstNonBlank(attrValue(attrs, "gender"), attrValue(attrs, "sex"));
        String bio = firstNonBlank(attrValue(attrs, "bio"), attrValue(attrs, "description"), attrValue(attrs, "about"));
        String birthday = firstNonBlank(attrValue(attrs, "birthday"), attrValue(attrs, "birthdate"));
        String country = firstNonBlank(attrValue(attrs, "country"), attrValue(attrs, "region"));
        String language = firstNonBlank(attrValue(attrs, "locale"), attrValue(attrs, "language"));
        String timezone = firstNonBlank(attrValue(attrs, "timezone"), attrValue(attrs, "time_zone"));

        return UserProfileDraft.builder()
                .firstName(extractFirstName(registrationId, attrs))
                .lastName(extractLastName(registrationId, attrs))
                .gender(normalizeGender(gender))
                .bio(bio)
                .birthday(parseBirthday(birthday))
                .country(country)
                .language(normalizeLanguage(language))
                .timezone(timezone)
                .build();
    }

    /**
     * 提取名（first_name）。
     * 优先 provider 的明确字段；否则从 name 简单拆分。
     */
    private String extractFirstName(String registrationId, Map<String, Object> attrs) {
        String direct = switch (registrationId) {
            case "google" -> firstNonBlank(
                    attrValue(attrs, "given_name"),
                    attrValue(attrs, "first_name")
            );
            case "github" -> attrValue(attrs, "name");
            case "microsoft" -> firstNonBlank(
                    attrValue(attrs, "given_name"),
                    attrValue(attrs, "name")
            );
            default -> null;
        };

        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }

        String name = attrValue(attrs, "name");
        if (name == null || name.isBlank()) {
            return null;
        }

        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }

        StringBuilder first = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                first.append(' ');
            }
            first.append(parts[i]);
        }
        return first.toString();
    }

    /**
     * 提取姓（last_name）。
     * 优先 provider 的明确字段；否则从 name 简单拆分。
     */
    private String extractLastName(String registrationId, Map<String, Object> attrs) {
        String direct = switch (registrationId) {
            case "google" -> firstNonBlank(
                    attrValue(attrs, "family_name"),
                    attrValue(attrs, "last_name")
            );
            case "microsoft" -> attrValue(attrs, "family_name");
            default -> null;
        };

        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }

        String name = attrValue(attrs, "name");
        if (name == null || name.isBlank()) {
            return null;
        }

        String[] parts = name.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        return parts[parts.length - 1];
    }

    /**
     * 归一化性别值到数据库约束枚举。
     */
    private String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "male", "m" -> "MALE";
            case "female", "f" -> "FEMALE";
            case "other", "non-binary", "nonbinary" -> "OTHER";
            case "unknown", "u" -> "UNKNOWN";
            default -> null;
        };
    }

    /**
     * 解析生日，当前支持 yyyy-MM-dd。
     */
    private LocalDate parseBirthday(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * 规范化语言字段，保持 provider 原始值语义。
     */
    private String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
