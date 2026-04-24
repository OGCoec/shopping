# Google OAuth 登录（无令牌版）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 shopping 项目新增 Google OAuth 登录接口，完成 Google 身份绑定/创建，并在成功后跳回 `login.html`，不生成令牌、不建立本地登录态。

**Architecture:** 参考现有 GitHub OAuth 链路，新增独立的 Google 控制器、服务与 Redis state 常量。控制器负责 OAuth 协议流程与回调跳转，服务负责按 `googleId/email` 绑定或创建 `UserLoginIdentity`，整体不引入新的认证过滤器或 token 体系。

**Tech Stack:** Spring Boot 3.5.5、Spring Web、Spring Security、Spring Data Redis、MyBatis、PostgreSQL、JUnit 5、Mockito

---

### Task 1: 补齐数据库访问能力

**Files:**
- Modify: `src/main/java/com/example/ShoppingSystem/mapper/UserLoginIdentityMapper.java`
- Modify: `src/main/resources/mapper/UserLoginIdentityMapper.xml`
- Test: `src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java`

- [ ] **Step 1: 写失败测试，约束 Mapper 需要支持 Google 身份绑定逻辑**

```java
package com.example.ShoppingSystem.service;

import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private UserLoginIdentityMapper userLoginIdentityMapper;

    @Mock
    private SnowflakeIdWorker snowflakeIdWorker;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @Test
    void shouldBindGoogleIdWhenEmailAlreadyExists() {
        UserLoginIdentity existing = new UserLoginIdentity();
        existing.setId(1001L);
        existing.setEmail("user@example.com");

        when(userLoginIdentityMapper.findByGoogleId("google-123")).thenReturn(null);
        when(userLoginIdentityMapper.findByEmail("user@example.com")).thenReturn(existing);
        when(userLoginIdentityMapper.bindGoogleIdById(1001L, "google-123")).thenReturn(1);

        UserLoginIdentity result = googleAuthService.loginByGoogle("google-123", "user@example.com");

        assertThat(result.getId()).isEqualTo(1001L);
        assertThat(result.getGoogleId()).isEqualTo("google-123");
    }
}
```

- [ ] **Step 2: 运行测试，确认当前因 GoogleAuthService / Google Mapper 方法不存在而失败**

Run: `./mvnw -Dtest=GoogleAuthServiceTest test`
Expected: FAIL，报 `GoogleAuthService` 或 `findByGoogleId` / `bindGoogleIdById` 未定义

- [ ] **Step 3: 在 Mapper 接口中新增 Google 查询与绑定方法**

修改 `src/main/java/com/example/ShoppingSystem/mapper/UserLoginIdentityMapper.java`，在现有 `findByGithubId`、`bindGithubIdById` 旁边补充：

```java
@Select("""
        SELECT id, user_id, email, email_password_hash, email_verified,
               phone, phone_verified, github_id, google_id, microsoft_id,
               token_version, status, last_login_at, created_at, updated_at
        FROM user_login_identity
        WHERE google_id = #{googleId}
        LIMIT 1
        """)
UserLoginIdentity findByGoogleId(@Param("googleId") String googleId);

@Update("""
        UPDATE user_login_identity
        SET google_id = #{googleId},
            last_login_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id}
        """)
int bindGoogleIdById(@Param("id") Long id, @Param("googleId") String googleId);

int insertGoogleIdentity(UserLoginIdentity entity);
```

- [ ] **Step 4: 在 Mapper XML 中新增 Google 插入语句**

修改 `src/main/resources/mapper/UserLoginIdentityMapper.xml`，在现有 `insertGithubIdentity` 后追加：

```xml
<insert id="insertGoogleIdentity" parameterType="com.example.ShoppingSystem.entity.entity.UserLoginIdentity">
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

- [ ] **Step 5: 再次运行测试，确认仍因 GoogleAuthService 未实现而失败**

Run: `./mvnw -Dtest=GoogleAuthServiceTest test`
Expected: FAIL，报 `GoogleAuthService` 不存在

- [ ] **Step 6: 提交当前变更**

```bash
git add src/main/java/com/example/ShoppingSystem/mapper/UserLoginIdentityMapper.java src/main/resources/mapper/UserLoginIdentityMapper.xml src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java
git commit -m "feat: add google identity mapper support"
```

### Task 2: 实现 Google 身份绑定服务

**Files:**
- Create: `src/main/java/com/example/ShoppingSystem/service/GoogleAuthService.java`
- Modify: `src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java`

- [ ] **Step 1: 扩展失败测试，覆盖三种核心路径**

将 `src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java` 扩展为：

```java
@Test
void shouldReturnExistingIdentityWhenGoogleIdAlreadyExists() {
    UserLoginIdentity existing = new UserLoginIdentity();
    existing.setId(2001L);
    existing.setGoogleId("google-existing");

    when(userLoginIdentityMapper.findByGoogleId("google-existing")).thenReturn(existing);

    UserLoginIdentity result = googleAuthService.loginByGoogle("google-existing", "user@example.com");

    assertThat(result.getId()).isEqualTo(2001L);
    assertThat(result.getGoogleId()).isEqualTo("google-existing");
}

@Test
void shouldCreateIdentityWhenEmailDoesNotExist() {
    when(snowflakeIdWorker.nextId()).thenReturn(3001L);
    when(userLoginIdentityMapper.findByGoogleId("google-new")).thenReturn(null);
    when(userLoginIdentityMapper.findByEmail("new@example.com")).thenReturn(null);
    when(userLoginIdentityMapper.insertGoogleIdentity(any(UserLoginIdentity.class))).thenReturn(1);

    UserLoginIdentity result = googleAuthService.loginByGoogle("google-new", "new@example.com");

    assertThat(result.getId()).isEqualTo(3001L);
    assertThat(result.getGoogleId()).isEqualTo("google-new");
    assertThat(result.getEmail()).isEqualTo("new@example.com");
}

@Test
void shouldCreateIdentityWithoutEmailWhenGoogleDoesNotReturnEmail() {
    when(snowflakeIdWorker.nextId()).thenReturn(4001L);
    when(userLoginIdentityMapper.findByGoogleId("google-no-email")).thenReturn(null);
    when(userLoginIdentityMapper.insertGoogleIdentity(any(UserLoginIdentity.class))).thenReturn(1);

    UserLoginIdentity result = googleAuthService.loginByGoogle("google-no-email", null);

    assertThat(result.getId()).isEqualTo(4001L);
    assertThat(result.getGoogleId()).isEqualTo("google-no-email");
    assertThat(result.getEmail()).isNull();
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./mvnw -Dtest=GoogleAuthServiceTest test`
Expected: FAIL，报 `GoogleAuthService` 不存在

- [ ] **Step 3: 新建 GoogleAuthService，并复用 GithubAuthService 风格实现**

创建 `src/main/java/com/example/ShoppingSystem/service/GoogleAuthService.java`：

```java
package com.example.ShoppingSystem.service;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class GoogleAuthService {

    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public GoogleAuthService(UserLoginIdentityMapper userLoginIdentityMapper,
                             SnowflakeIdWorker snowflakeIdWorker) {
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    /**
     * 使用 Google 身份完成本地绑定或创建。
     */
    @Transactional
    public UserLoginIdentity loginByGoogle(String googleId, String googleEmail) {
        UserLoginIdentity existingByGoogle = userLoginIdentityMapper.findByGoogleId(googleId);
        if (existingByGoogle != null) {
            userLoginIdentityMapper.updateLastLoginAtById(existingByGoogle.getId());
            return existingByGoogle;
        }

        if (googleEmail != null && !googleEmail.isBlank()) {
            String normalizedEmail = googleEmail.trim().toLowerCase();
            UserLoginIdentity existingByEmail = userLoginIdentityMapper.findByEmail(normalizedEmail);
            if (existingByEmail != null) {
                userLoginIdentityMapper.bindGoogleIdById(existingByEmail.getId(), googleId);
                existingByEmail.setGoogleId(googleId);
                existingByEmail.setLastLoginAt(OffsetDateTime.now());
                return existingByEmail;
            }

            UserLoginIdentity created = buildNewGoogleIdentity(googleId, normalizedEmail);
            userLoginIdentityMapper.insertGoogleIdentity(created);
            return created;
        }

        UserLoginIdentity created = buildNewGoogleIdentity(googleId, null);
        userLoginIdentityMapper.insertGoogleIdentity(created);
        return created;
    }

    /**
     * 构造新的 Google 登录身份记录。
     */
    private UserLoginIdentity buildNewGoogleIdentity(String googleId, String email) {
        OffsetDateTime now = OffsetDateTime.now();
        UserLoginIdentity entity = new UserLoginIdentity();
        entity.setId(snowflakeIdWorker.nextId());
        entity.setUserId(0L);
        entity.setEmail(email);
        entity.setEmailPasswordHash(null);
        entity.setEmailVerified(Boolean.FALSE);
        entity.setPhone(null);
        entity.setPhoneVerified(Boolean.FALSE);
        entity.setGithubId(null);
        entity.setGoogleId(googleId);
        entity.setMicrosoftId(null);
        entity.setTokenVersion(IdUtil.fastSimpleUUID().substring(0, 24));
        entity.setStatus("ACTIVE");
        entity.setLastLoginAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
```

- [ ] **Step 4: 运行测试，确认服务逻辑通过**

Run: `./mvnw -Dtest=GoogleAuthServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交当前变更**

```bash
git add src/main/java/com/example/ShoppingSystem/service/GoogleAuthService.java src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java
git commit -m "feat: add google identity binding service"
```

### Task 3: 新增 Google OAuth state 常量与回调控制器

**Files:**
- Create: `src/main/java/com/example/ShoppingSystem/redisdata/GoogleRedisKeys.java`
- Create: `src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java`
- Test: `src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java`

- [ ] **Step 1: 写控制器失败测试，约束跳转地址与回调行为**

创建 `src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java`：

```java
package com.example.ShoppingSystem.controller;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.service.GoogleAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GoogleAuthController.class)
class GoogleAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private GoogleAuthService googleAuthService;

    @Test
    void shouldRedirectToGoogleAuthorizeUrl() throws Exception {
        mockMvc.perform(get("/oauth2/google/login").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://accounts.google.com/**"));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `./mvnw -Dtest=GoogleAuthControllerTest test`
Expected: FAIL，报 `GoogleAuthController` 不存在

- [ ] **Step 3: 新增 GoogleRedisKeys**

创建 `src/main/java/com/example/ShoppingSystem/redisdata/GoogleRedisKeys.java`：

```java
package com.example.ShoppingSystem.redisdata;

public final class GoogleRedisKeys {

    private GoogleRedisKeys() {
    }

    public static final String STATE_PREFIX = "auth:google:state:";
    public static final long STATE_TTL_MINUTES = 5L;
}
```

- [ ] **Step 4: 新增 GoogleAuthController，先打通主流程与跳转协议**

创建 `src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java`：

```java
package com.example.ShoppingSystem.controller;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.redisdata.GoogleRedisKeys;
import com.example.ShoppingSystem.service.GoogleAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
public class GoogleAuthController {

    private static final String GOOGLE_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String LOGIN_SUCCESS_URL = "http://localhost:6655/login.html?google=success";
    private static final String LOGIN_FAILED_URL = "http://localhost:6655/login.html?google=failed";

    private final StringRedisTemplate stringRedisTemplate;
    private final GoogleAuthService googleAuthService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${app.google.redirect-uri:http://localhost:6655/login/google/callback}")
    private String redirectUri;

    public GoogleAuthController(StringRedisTemplate stringRedisTemplate,
                                GoogleAuthService googleAuthService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.googleAuthService = googleAuthService;
    }

    /**
     * 发起 Google OAuth 授权。
     */
    @GetMapping("/oauth2/google/login")
    public void redirectToGoogle(HttpServletResponse response) throws IOException {
        String state = IdUtil.nanoId(48);
        String redisKey = GoogleRedisKeys.STATE_PREFIX + state;
        stringRedisTemplate.opsForValue().set(
                redisKey,
                "google_login",
                GoogleRedisKeys.STATE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        String authorizeUrl = GOOGLE_AUTHORIZE_URL
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=openid%20profile%20email"
                + "&state=" + state;

        response.sendRedirect(authorizeUrl);
    }

    /**
     * 处理 Google OAuth 回调。
     */
    @GetMapping("/login/google/callback")
    public void handleCallback(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @RequestParam(required = false) String error,
                               HttpServletResponse response) throws IOException {
        if (state == null) {
            response.sendRedirect(LOGIN_FAILED_URL + "&msg=missing_state");
            return;
        }

        String redisKey = GoogleRedisKeys.STATE_PREFIX + state;
        String stateValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (stateValue == null) {
            response.sendRedirect(LOGIN_FAILED_URL + "&msg=invalid_state");
            return;
        }
        stringRedisTemplate.delete(redisKey);

        if (error != null || code == null) {
            response.sendRedirect(LOGIN_FAILED_URL + "&msg=oauth_cancelled");
            return;
        }

        try {
            String accessToken = exchangeCodeForToken(code);
            if (accessToken == null) {
                response.sendRedirect(LOGIN_FAILED_URL + "&msg=get_token_failed");
                return;
            }

            Map<String, Object> googleUser = fetchGoogleUser(accessToken);
            if (googleUser == null) {
                response.sendRedirect(LOGIN_FAILED_URL + "&msg=get_user_failed");
                return;
            }

            String googleId = String.valueOf(googleUser.get("sub"));
            String googleEmail = resolveGoogleEmail(googleUser);
            UserLoginIdentity identity = googleAuthService.loginByGoogle(googleId, googleEmail);
            if (identity == null) {
                response.sendRedirect(LOGIN_FAILED_URL + "&msg=bind_failed");
                return;
            }

            response.sendRedirect(LOGIN_SUCCESS_URL);
        } catch (Exception e) {
            response.sendRedirect(LOGIN_FAILED_URL + "&msg=sys_error");
        }
    }

    @SuppressWarnings("unchecked")
    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                GOOGLE_TOKEN_URL,
                HttpMethod.POST,
                requestEntity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
        );

        if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
            Object token = responseEntity.getBody().get("access_token");
            return token != null ? token.toString() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchGoogleUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                GOOGLE_USER_INFO_URL,
                HttpMethod.GET,
                requestEntity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
        );

        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        }
        return null;
    }

    /**
     * 提取并标准化 Google 邮箱。
     */
    private String resolveGoogleEmail(Map<String, Object> googleUser) {
        Object directEmail = googleUser.get("email");
        if (directEmail instanceof String email && !email.isBlank()) {
            return email.trim().toLowerCase();
        }
        return null;
    }
}
```

- [ ] **Step 5: 运行测试，确认登录入口跳转测试通过**

Run: `./mvnw -Dtest=GoogleAuthControllerTest test`
Expected: PASS

- [ ] **Step 6: 提交当前变更**

```bash
git add src/main/java/com/example/ShoppingSystem/redisdata/GoogleRedisKeys.java src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java
git commit -m "feat: add google oauth controller"
```

### Task 4: 放行安全规则与补充配置项

**Files:**
- Modify: `src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java`

- [ ] **Step 1: 写失败测试，要求回调接口可匿名访问**

在 `src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java` 增加：

```java
@Test
void shouldAllowAnonymousAccessToGoogleCallback() throws Exception {
    mockMvc.perform(get("/login/google/callback"))
            .andExpect(status().is3xxRedirection());
}
```

- [ ] **Step 2: 运行测试，确认因安全放行不足或配置缺失而失败**

Run: `./mvnw -Dtest=GoogleAuthControllerTest test`
Expected: FAIL，若当前安全规则未放行 `/oauth2/google/login` 与 `/login/google/callback`，则返回 401/403 或配置错误

- [ ] **Step 3: 修改 SecurityConfig，放行 Google 登录入口与回调地址**

修改 `src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java` 第 18 行附近的 `requestMatchers(...)`：

```java
.requestMatchers(
        "/",
        "/index.html",
        "/login.html",
        "/css/**",
        "/js/**",
        "/images/**",
        "/webjars/**",
        "/error",
        "/oauth2/github/login",
        "/login/github/callback",
        "/oauth2/google/login",
        "/login/google/callback"
)
.permitAll()
```

- [ ] **Step 4: 修改 application.yaml，新增 Google 回调配置**

在 `src/main/resources/application.yaml` 的 `app:` 节点下追加：

```yaml
app:
  github:
    redirect-uri: http://localhost:6655/login/github/callback
  google:
    redirect-uri: http://localhost:6655/login/google/callback
```

同时确认已有：

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
```

- [ ] **Step 5: 运行测试，确认匿名访问与跳转规则通过**

Run: `./mvnw -Dtest=GoogleAuthControllerTest test`
Expected: PASS

- [ ] **Step 6: 提交当前变更**

```bash
git add src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java src/main/resources/application.yaml src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java
git commit -m "feat: configure google oauth routes"
```

### Task 5: 补充回调失败场景与邮箱标准化验证

**Files:**
- Modify: `src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java`
- Modify: `src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java`

- [ ] **Step 1: 给控制器补充失败场景测试**

在 `src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java` 追加：

```java
@Test
void shouldRedirectToFailedPageWhenStateIsMissing() throws Exception {
    mockMvc.perform(get("/login/google/callback"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:6655/login.html?google=failed&msg=missing_state"));
}

@Test
void shouldRedirectToFailedPageWhenOauthReturnsError() throws Exception {
    when(stringRedisTemplate.opsForValue().get("auth:google:state:test-state")).thenReturn("google_login");

    mockMvc.perform(get("/login/google/callback")
                    .param("state", "test-state")
                    .param("error", "access_denied"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:6655/login.html?google=failed&msg=oauth_cancelled"));
}
```

- [ ] **Step 2: 给服务补充邮箱标准化测试**

在 `src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java` 追加：

```java
@Test
void shouldNormalizeGoogleEmailBeforeQueryingExistingIdentity() {
    when(userLoginIdentityMapper.findByGoogleId("google-normalize")).thenReturn(null);
    when(userLoginIdentityMapper.findByEmail("mixed@example.com")).thenReturn(null);
    when(snowflakeIdWorker.nextId()).thenReturn(5001L);
    when(userLoginIdentityMapper.insertGoogleIdentity(any(UserLoginIdentity.class))).thenReturn(1);

    UserLoginIdentity result = googleAuthService.loginByGoogle("google-normalize", "  Mixed@Example.com ");

    assertThat(result.getEmail()).isEqualTo("mixed@example.com");
}
```

- [ ] **Step 3: 运行测试，确认新增约束通过**

Run: `./mvnw -Dtest=GoogleAuthControllerTest,GoogleAuthServiceTest test`
Expected: PASS

- [ ] **Step 4: 执行一次更接近真实集成的测试集**

Run: `./mvnw test -Dtest=GoogleAuthControllerTest,GoogleAuthServiceTest,DatabaseConnectionTest`
Expected: PASS

- [ ] **Step 5: 提交当前变更**

```bash
git add src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java
git commit -m "test: cover google oauth failure scenarios"
```

### Task 6: 手工联调与验收

**Files:**
- Modify: `src/main/resources/application.yaml`（如需改本地回调地址）
- Verify: `src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java`

- [ ] **Step 1: 设置本地环境变量**

Run:

```bash
export GOOGLE_CLIENT_ID="your-google-client-id"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"
```

Expected: shell 中成功注入变量，无报错

- [ ] **Step 2: 启动应用**

Run: `./mvnw spring-boot:run`
Expected: 应用启动成功，监听 `http://localhost:6655`

- [ ] **Step 3: 访问 Google 登录入口**

Open: `http://localhost:6655/oauth2/google/login`
Expected: 浏览器跳转到 Google 授权页面

- [ ] **Step 4: 完成 Google 授权并检查回调结果**

Expected:
- 首次授权时，本地 `user_login_identity.google_id` 被写入或新增记录
- 成功后跳转到 `http://localhost:6655/login.html?google=success`
- 失败时跳转到 `http://localhost:6655/login.html?google=failed&msg=<reason>`

- [ ] **Step 5: 复查数据库记录**

Run:

```bash
psql -h 127.0.0.1 -U postgres -d shopping -c "select id,email,github_id,google_id,status,last_login_at from user_login_identity order by updated_at desc limit 5;"
```

Expected: 能看到本次 Google 登录对应的 `google_id` 绑定或新建记录

- [ ] **Step 6: 提交最终变更**

```bash
git add src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java src/main/java/com/example/ShoppingSystem/service/GoogleAuthService.java src/main/java/com/example/ShoppingSystem/redisdata/GoogleRedisKeys.java src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java src/main/java/com/example/ShoppingSystem/mapper/UserLoginIdentityMapper.java src/main/resources/mapper/UserLoginIdentityMapper.xml src/main/resources/application.yaml src/test/java/com/example/ShoppingSystem/controller/GoogleAuthControllerTest.java src/test/java/com/example/ShoppingSystem/service/GoogleAuthServiceTest.java
git commit -m "feat: add google oauth identity binding flow"
```

## Self-Review
- 规格覆盖：已覆盖 Google 登录入口、回调、Redis state、身份绑定/创建、安全放行、配置补充、失败跳转与联调验收。
- 占位检查：无 TBD/TODO；每个任务均给出明确文件、代码与命令。
- 一致性检查：当前计划已统一为“无令牌版”，成功回调目标固定为 `login.html?google=success`，不再包含建立本地登录态的步骤。
