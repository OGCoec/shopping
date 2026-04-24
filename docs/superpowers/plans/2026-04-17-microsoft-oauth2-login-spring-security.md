# Microsoft OAuth2（方案 C：Spring Security 原生）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 GitHub/Google 手写 OAuth 流程切换为 Spring Security 原生 OAuth2 Login，并新增 Microsoft（common + User.Read + Graph /me 邮箱兜底）登录，统一走成功/失败处理器回跳登录页。

**Architecture:** 认证入口使用 `/oauth2/authorization/{registrationId}`，回调使用 `/login/oauth2/code/{registrationId}`，由 Spring Security 过滤器处理授权码交换与用户认证。业务层通过统一成功处理器按 provider 调用 `GithubAuthService`/`GoogleAuthService`/`MicrosoftAuthService` 做本地身份绑定，Microsoft 在缺少 email 时调用 Graph `/v1.0/me` 获取邮箱。前端继续使用现有 `/oauth2/{provider}/login` 按钮入口，由轻量控制器做 302 转发并保留 Swagger 文档。

**Tech Stack:** Spring Boot 3.5.5、Spring Security OAuth2 Client、Spring Web、MyBatis、PostgreSQL、Redis（可保留但本方案不依赖 OAuth state 存储）、Swagger/OpenAPI

---

> 用户要求：本计划不包含测试编写步骤；保留编译与手工联调验证步骤。新增代码注释使用简体中文，并保留 Swagger 注解。

### Task 1: 新增 Microsoft 身份绑定服务与持久化能力

**Files:**
- Create: `shopping-service/src/main/java/com/example/ShoppingSystem/service/MicrosoftAuthService.java`
- Modify: `shopping-mapper/src/main/java/com/example/ShoppingSystem/mapper/UserLoginIdentityMapper.java`
- Modify: `shopping-mapper/src/main/resources/mapper/UserLoginIdentityMapper.xml`

- [ ] **Step 1: 在 Mapper 接口补齐 Microsoft 查询/绑定/插入方法**

修改 `shopping-mapper/src/main/java/com/example/ShoppingSystem/mapper/UserLoginIdentityMapper.java`，追加以下方法（保持与 GitHub/Google 同风格）：

```java
@Select("""
        SELECT id, user_id, email, email_password_hash, email_verified,
               phone, phone_verified, github_id, google_id, microsoft_id,
               token_version, status, last_login_at, created_at, updated_at
        FROM user_login_identity
        WHERE microsoft_id = #{microsoftId}
        LIMIT 1
        """)
UserLoginIdentity findByMicrosoftId(@Param("microsoftId") String microsoftId);

@Update("""
        UPDATE user_login_identity
        SET microsoft_id = #{microsoftId},
            last_login_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
        """)
int bindMicrosoftIdById(@Param("id") Long id, @Param("microsoftId") String microsoftId);

int insertMicrosoftIdentity(UserLoginIdentity entity);
```

- [ ] **Step 2: 在 Mapper XML 增加 Microsoft 插入 SQL**

修改 `shopping-mapper/src/main/resources/mapper/UserLoginIdentityMapper.xml`，新增：

```xml
<insert id="insertMicrosoftIdentity" parameterType="com.example.ShoppingSystem.entity.entity.UserLoginIdentity">
    INSERT INTO user_login_identity (
        id,
        user_id,
        email,
        email_password_hash,
        email_verified,
        phone,
        phone_verified,
        github_id,
        google_id,
        microsoft_id,
        token_version,
        status,
        last_login_at,
        created_at,
        updated_at
    ) VALUES (
        #{id},
        #{userId},
        #{email},
        #{emailPasswordHash},
        #{emailVerified},
        #{phone},
        #{phoneVerified},
        #{githubId},
        #{googleId},
        #{microsoftId},
        #{tokenVersion},
        #{status},
        #{lastLoginAt},
        #{createdAt},
        #{updatedAt}
    )
</insert>
```

- [ ] **Step 3: 新建 MicrosoftAuthService（中文注释）**

创建 `shopping-service/src/main/java/com/example/ShoppingSystem/service/MicrosoftAuthService.java`：

```java
package com.example.ShoppingSystem.service;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Microsoft 登录身份服务。
 * 负责根据 microsoftId / email 完成本地身份查询、绑定或创建。
 */
@Service
public class MicrosoftAuthService {

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public MicrosoftAuthService(UserLoginIdentityMapper userLoginIdentityMapper,
                                SnowflakeIdWorker snowflakeIdWorker) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    /**
     * 使用 Microsoft 身份完成本地绑定或创建。
     */
    @Transactional
    public UserLoginIdentity loginByMicrosoft(String microsoftId, String microsoftEmail) {
        UserLoginIdentity existingByMicrosoft = userLoginIdentityMapper.findByMicrosoftId(microsoftId);
        if (existingByMicrosoft != null) {
            userLoginIdentityMapper.updateLastLoginAtById(existingByMicrosoft.getId());
            return existingByMicrosoft;
        }

        if (microsoftEmail != null && !microsoftEmail.isBlank()) {
            String normalizedEmail = microsoftEmail.trim().toLowerCase();
            UserLoginIdentity existingByEmail = userLoginIdentityMapper.findByEmail(normalizedEmail);
            if (existingByEmail != null) {
                userLoginIdentityMapper.bindMicrosoftIdById(existingByEmail.getId(), microsoftId);
                existingByEmail.setMicrosoftId(microsoftId);
                existingByEmail.setLastLoginAt(OffsetDateTime.now());
                return existingByEmail;
            }

            UserLoginIdentity created = buildNewMicrosoftIdentity(microsoftId, normalizedEmail);
            userLoginIdentityMapper.insertMicrosoftIdentity(created);
            return created;
        }

        UserLoginIdentity created = buildNewMicrosoftIdentity(microsoftId, null);
        userLoginIdentityMapper.insertMicrosoftIdentity(created);
        return created;
    }

    /**
     * 构造新的 Microsoft 登录身份记录。
     */
    private UserLoginIdentity buildNewMicrosoftIdentity(String microsoftId, String email) {
        OffsetDateTime now = OffsetDateTime.now();
        return UserLoginIdentity.builder()
                .id(snowflakeIdWorker.nextId())
                .userId(0L)
                .email(email)
                .emailPasswordHash(null)
                .emailVerified(Boolean.FALSE)
                .phone(null)
                .phoneVerified(Boolean.FALSE)
                .githubId(null)
                .googleId(null)
                .microsoftId(microsoftId)
                .tokenVersion(IdUtil.fastSimpleUUID().substring(0, 24))
                .status("ACTIVE")
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
```

- [ ] **Step 4: 编译检查（不跑测试）**

Run: `./mvnw -DskipTests -pl shopping-service,shopping-mapper -am compile`
Expected: `BUILD SUCCESS`

---

### Task 2: 用 Spring Security 原生 oauth2Login 接管回调，并统一成功/失败处理

**Files:**
- Create: `shopping-web/src/main/java/com/example/ShoppingSystem/security/OAuth2LoginSuccessHandler.java`
- Create: `shopping-web/src/main/java/com/example/ShoppingSystem/security/OAuth2LoginFailureHandler.java`
- Modify: `shopping-web/src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java`

- [ ] **Step 1: 新建成功处理器，统一 provider 绑定逻辑（中文注释）**

创建 `shopping-web/src/main/java/com/example/ShoppingSystem/security/OAuth2LoginSuccessHandler.java`：

```java
package com.example.ShoppingSystem.security;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.service.GithubAuthService;
import com.example.ShoppingSystem.service.GoogleAuthService;
import com.example.ShoppingSystem.service.MicrosoftAuthService;
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
import java.util.Map;

/**
 * OAuth2 登录成功处理器。
 * 负责根据 provider 完成本地身份绑定，并回跳 login.html。
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final GithubAuthService githubAuthService;
    private final GoogleAuthService googleAuthService;
    private final MicrosoftAuthService microsoftAuthService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate = new RestTemplate();

    public OAuth2LoginSuccessHandler(GithubAuthService githubAuthService,
                                     GoogleAuthService googleAuthService,
                                     MicrosoftAuthService microsoftAuthService,
                                     OAuth2AuthorizedClientService authorizedClientService) {
        this.githubAuthService = githubAuthService;
        this.googleAuthService = googleAuthService;
        this.microsoftAuthService = microsoftAuthService;
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

            response.sendRedirect("http://localhost:6655/login.html?" + registrationId + "=success");
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
        return "http://localhost:6655/login.html?" + rid + "=failed&msg=" + msg;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
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
}
```

- [ ] **Step 2: 新建失败处理器，统一失败回跳**

创建 `shopping-web/src/main/java/com/example/ShoppingSystem/security/OAuth2LoginFailureHandler.java`：

```java
package com.example.ShoppingSystem.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 登录失败处理器。
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String msg = URLEncoder.encode("oauth_failed", StandardCharsets.UTF_8);
        response.sendRedirect("http://localhost:6655/login.html?oauth=failed&msg=" + msg);
    }
}
```

- [ ] **Step 3: 改造 SecurityConfig 接入 oauth2Login**

修改 `shopping-web/src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java`：

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                OAuth2LoginSuccessHandler successHandler,
                                                OAuth2LoginFailureHandler failureHandler) throws Exception {
    return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/", "/index.html", "/login.html", "/css/**", "/js/**", "/images/**", "/webjars/**", "/error",
                            "/oauth2/github/login", "/oauth2/google/login", "/oauth2/microsoft/login",
                            "/oauth2/authorization/**", "/login/oauth2/code/**"
                    ).permitAll()
                    .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                    .successHandler(successHandler)
                    .failureHandler(failureHandler))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .build();
}
```

- [ ] **Step 4: 编译检查（不跑测试）**

Run: `./mvnw -DskipTests -pl shopping-web -am compile`
Expected: `BUILD SUCCESS`

---

### Task 3: 新建 Swagger 化 OAuth 入口控制器并移除旧手写控制器

**Files:**
- Create: `shopping-web/src/main/java/com/example/ShoppingSystem/controller/OAuth2LoginEntryController.java`
- Delete: `shopping-web/src/main/java/com/example/ShoppingSystem/controller/GithubAuthController.java`
- Delete: `shopping-web/src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java`

- [ ] **Step 1: 新建统一入口控制器（保留 Swagger + 中文注释）**

创建 `shopping-web/src/main/java/com/example/ShoppingSystem/controller/OAuth2LoginEntryController.java`：

```java
package com.example.ShoppingSystem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

/**
 * OAuth2 登录入口控制器。
 * 仅负责把前端自定义入口转发到 Spring Security 标准入口。
 */
@Tag(name = "第三方登录入口", description = "GitHub/Google/Microsoft 登录发起入口")
@Controller
public class OAuth2LoginEntryController {

    @Operation(summary = "发起 GitHub 登录", description = "302 跳转到 Spring Security 标准授权入口")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "重定向到 /oauth2/authorization/github")})
    @GetMapping("/oauth2/github/login")
    public void loginByGithub(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/github");
    }

    @Operation(summary = "发起 Google 登录", description = "302 跳转到 Spring Security 标准授权入口")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "重定向到 /oauth2/authorization/google")})
    @GetMapping("/oauth2/google/login")
    public void loginByGoogle(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @Operation(summary = "发起 Microsoft 登录", description = "302 跳转到 Spring Security 标准授权入口")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "重定向到 /oauth2/authorization/microsoft")})
    @GetMapping("/oauth2/microsoft/login")
    public void loginByMicrosoft(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/microsoft");
    }
}
```

- [ ] **Step 2: 删除旧的手写 OAuth 控制器**

删除以下文件：
- `shopping-web/src/main/java/com/example/ShoppingSystem/controller/GithubAuthController.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java`

- [ ] **Step 3: 编译检查（不跑测试）**

Run: `./mvnw -DskipTests -pl shopping-web -am compile`
Expected: `BUILD SUCCESS`

---

### Task 4: 配置 Microsoft 客户端与前端按钮事件

**Files:**
- Modify: `shopping-web/src/main/resources/application.yaml`
- Modify: `shopping-web/src/main/resources/static/js/login.js`

- [ ] **Step 1: 在 application.yaml 增加 Microsoft registration/provider**

修改 `shopping-web/src/main/resources/application.yaml`，在 `spring.security.oauth2.client` 下追加：

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          microsoft:
            client-id: ${AZURE_CLIENT_ID}
            client-secret: ${AZURE_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - User.Read
        provider:
          microsoft:
            authorization-uri: https://login.microsoftonline.com/common/oauth2/v2.0/authorize
            token-uri: https://login.microsoftonline.com/common/oauth2/v2.0/token
            user-info-uri: https://graph.microsoft.com/v1.0/me
            user-name-attribute: id
```

> 说明：本方案明确使用 `common`，支持“任何 Entra ID 租户 + 个人 Microsoft 帐户”；并通过 `User.Read` 允许读取 Graph `/me`。

- [ ] **Step 2: 给微软按钮补充点击事件**

修改 `shopping-web/src/main/resources/static/js/login.js`：

```javascript
const microsoftBtn = document.getElementById("btn-microsoft");

if (microsoftBtn) {
  microsoftBtn.addEventListener("click", () => {
    window.location.href = "/oauth2/microsoft/login";
  });
}
```

- [ ] **Step 3: 编译检查（不跑测试）**

Run: `./mvnw -DskipTests compile`
Expected: `BUILD SUCCESS`

---

### Task 5: 手工联调与验收（不写自动化测试）

**Files:**
- Verify: `shopping-web/src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java`
- Verify: `shopping-web/src/main/java/com/example/ShoppingSystem/security/OAuth2LoginSuccessHandler.java`
- Verify: `shopping-web/src/main/java/com/example/ShoppingSystem/controller/OAuth2LoginEntryController.java`
- Verify: `shopping-web/src/main/resources/application.yaml`

- [ ] **Step 1: 准备环境变量**

Run:

```bash
export GITHUB_CLIENT_ID="<github-client-id>"
export GITHUB_CLIENT_SECRET="<github-client-secret>"
export GOOGLE_CLIENT_ID="<google-client-id>"
export GOOGLE_CLIENT_SECRET="<google-client-secret>"
export AZURE_CLIENT_ID="<azure-client-id>"
export AZURE_CLIENT_SECRET="<azure-client-secret>"
```

Expected: 环境变量注入成功，无报错。

- [ ] **Step 2: 启动服务**

Run: `./mvnw -pl shopping-web -am spring-boot:run`
Expected: 应用启动成功，监听 `http://localhost:6655`。

- [ ] **Step 3: 验证 Swagger 入口文档**

Open: `http://localhost:6655/swagger-ui/index.html`（或你项目当前 swagger 入口）
Expected: 可见“第三方登录入口”分组与 3 个发起登录接口（GitHub/Google/Microsoft）。

- [ ] **Step 4: 验证三方登录跳转链路**

Open: `http://localhost:6655/login.html`，分别点击 3 个按钮。
Expected:
- GitHub：授权成功后跳 `login.html?github=success`，失败跳 `login.html?github=failed&msg=...`
- Google：授权成功后跳 `login.html?google=success`，失败跳 `login.html?google=failed&msg=...`
- Microsoft：授权成功后跳 `login.html?microsoft=success`，失败跳 `login.html?microsoft=failed&msg=...`

- [ ] **Step 5: 验证 Microsoft 邮箱兜底逻辑**

在成功登录后检查数据库最新记录：

```bash
psql -h 127.0.0.1 -U postgres -d shopping -c "select id,email,github_id,google_id,microsoft_id,last_login_at from user_login_identity order by updated_at desc limit 10;"
```

Expected: `microsoft_id` 被写入；若 claim 无 email，email 来自 Graph `/me` 的 `mail` 或 `userPrincipalName`。

---

## Self-Review
- 覆盖性：包含方案 C 所需的 security 接管、Microsoft 接入、Graph `/me` 邮箱兜底、前端入口、Swagger、中文注释、手工验收。
- 占位符检查：无 TBD/TODO/“后续实现”。
- 一致性：统一使用 `/oauth2/{provider}/login` 作为前端入口，统一成功/失败回跳格式，Microsoft 配置与 common + User.Read 保持一致。
