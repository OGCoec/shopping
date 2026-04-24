# OAuth 首次登录初始化 UserProfile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Google / Microsoft / GitHub OAuth 首次登录成功后，初始化一条 `user_profile`（`id = user_id`），并按 provider 原始字段尽量映射，缺失字段写 `NULL`。

**Architecture:** 继续沿用现有 OAuth 成功入口 `OAuth2LoginSuccessHandler`。先保持原有 `user_login_identity` 逻辑不变，再在成功后调用 `UserProfileService.initIfAbsent` 完成“存在即跳过、不存在则插入”。profile 字段映射在 SuccessHandler 聚合，写库仅由 service+mapper 负责。

**Tech Stack:** Spring Boot, Spring Security OAuth2, MyBatis, PostgreSQL, Lombok, Java 17

---

### Task 1: 新增 UserProfile 实体与 Draft 结构

**Files:**
- Create: `shopping-model/src/main/java/com/example/ShoppingSystem/entity/entity/UserProfile.java`
- Create: `shopping-service/src/main/java/com/example/ShoppingSystem/service/model/UserProfileDraft.java`

- [ ] **Step 1: 创建 `UserProfile` 实体（与表字段对应）**

```java
package com.example.ShoppingSystem.entity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private Long id;
    private String firstName;
    private String lastName;
    private String gender;
    private String bio;
    private LocalDate birthday;
    private String country;
    private String language;
    private String timezone;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 `UserProfileDraft`（仅承载初始化输入）**

```java
package com.example.ShoppingSystem.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDraft {
    private String firstName;
    private String lastName;
    private String gender;
    private String bio;
    private LocalDate birthday;
    private String country;
    private String language;
    private String timezone;
}
```

- [ ] **Step 3: 编译 model+service 模块检查类型**

Run: `mvn -pl shopping-model,shopping-service -am -DskipTests compile`

Expected: `BUILD SUCCESS`

---

### Task 2: 新增 UserProfileMapper 与 XML

**Files:**
- Create: `shopping-mapper/src/main/java/com/example/ShoppingSystem/mapper/UserProfileMapper.java`
- Create: `shopping-mapper/src/main/resources/mapper/UserProfileMapper.xml`

- [ ] **Step 1: 新增 Mapper 接口（exists + insert）**

```java
package com.example.ShoppingSystem.mapper;

import com.example.ShoppingSystem.entity.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProfileMapper {

    @Select("SELECT EXISTS (SELECT 1 FROM user_profile WHERE id = #{id})")
    boolean existsById(@Param("id") Long id);

    int insertUserProfile(UserProfile profile);
}
```

- [ ] **Step 2: 新增 XML（仅插入目标字段，不写 avatar/created_at/updated_at）**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.ShoppingSystem.mapper.UserProfileMapper">

    <insert id="insertUserProfile" parameterType="com.example.ShoppingSystem.entity.entity.UserProfile">
        INSERT INTO user_profile (
            id,
            first_name,
            last_name,
            gender,
            bio,
            birthday,
            country,
            language,
            timezone
        ) VALUES (
            #{id},
            #{firstName},
            #{lastName},
            #{gender},
            #{bio},
            #{birthday},
            #{country},
            #{language},
            #{timezone}
        )
    </insert>

</mapper>
```

- [ ] **Step 3: 编译 mapper 模块**

Run: `mvn -pl shopping-mapper -am -DskipTests compile`

Expected: `BUILD SUCCESS`

---

### Task 3: 新增 UserProfileService（首次登录初始化）

**Files:**
- Create: `shopping-service/src/main/java/com/example/ShoppingSystem/service/UserProfileService.java`

- [ ] **Step 1: 实现 initIfAbsent 服务方法**

```java
package com.example.ShoppingSystem.service;

import com.example.ShoppingSystem.entity.entity.UserProfile;
import com.example.ShoppingSystem.mapper.UserProfileMapper;
import com.example.ShoppingSystem.service.model.UserProfileDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;

    public UserProfileService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Transactional
    public void initIfAbsent(Long userId, UserProfileDraft draft) {
        if (userId == null || userProfileMapper.existsById(userId)) {
            return;
        }
        UserProfile profile = UserProfile.builder()
                .id(userId)
                .firstName(draft == null ? null : draft.getFirstName())
                .lastName(draft == null ? null : draft.getLastName())
                .gender(draft == null ? null : draft.getGender())
                .bio(draft == null ? null : draft.getBio())
                .birthday(draft == null ? null : draft.getBirthday())
                .country(draft == null ? null : draft.getCountry())
                .language(draft == null ? null : draft.getLanguage())
                .timezone(draft == null ? null : draft.getTimezone())
                .build();
        userProfileMapper.insertUserProfile(profile);
    }
}
```

- [ ] **Step 2: 编译 service 模块**

Run: `mvn -pl shopping-service -am -DskipTests compile`

Expected: `BUILD SUCCESS`

---

### Task 4: 在 OAuth2LoginSuccessHandler 中映射 provider attrs 并初始化 profile

**Files:**
- Modify: `shopping-web/src/main/java/com/example/ShoppingSystem/security/OAuth2LoginSuccessHandler.java`

- [ ] **Step 1: 注入 `UserProfileService` 并在登录成功后调用初始化**

```java
// 新增字段
private final UserProfileService userProfileService;

// 构造器新增参数
UserProfileService userProfileService

// switch 得到 identity 后、跳转前新增
try {
    UserProfileDraft draft = buildProfileDraft(registrationId, attrs);
    userProfileService.initIfAbsent(identity.getUserId(), draft);
} catch (Exception profileEx) {
    // 仅记录，不中断登录主链路
    log.warn("init user_profile failed, provider={}, userId={}", registrationId, identity.getUserId(), profileEx);
}
```

- [ ] **Step 2: 添加 profile 字段提取方法（尽量映射，拿不到 NULL）**

```java
private UserProfileDraft buildProfileDraft(String registrationId, Map<String, Object> attrs) {
    return UserProfileDraft.builder()
            .firstName(extractFirstName(registrationId, attrs))
            .lastName(extractLastName(registrationId, attrs))
            .gender(normalizeGender(stringValue(firstNonBlank(
                    stringValue(attrs.get("gender")),
                    stringValue(attrs.get("sex"))
            ))))
            .bio(stringValue(firstNonBlank(
                    stringValue(attrs.get("bio")),
                    stringValue(attrs.get("description")),
                    stringValue(attrs.get("about"))
            )))
            .birthday(parseBirthday(firstNonBlank(
                    stringValue(attrs.get("birthday")),
                    stringValue(attrs.get("birthdate"))
            )))
            .country(stringValue(firstNonBlank(
                    stringValue(attrs.get("country")),
                    stringValue(attrs.get("region"))
            )))
            .language(normalizeLanguage(firstNonBlank(
                    stringValue(attrs.get("locale")),
                    stringValue(attrs.get("language"))
            )))
            .timezone(stringValue(firstNonBlank(
                    stringValue(attrs.get("timezone")),
                    stringValue(attrs.get("time_zone"))
            )))
            .build();
}
```

- [ ] **Step 3: 添加姓名拆分、生日解析、性别归一化的私有工具方法**

```java
private String normalizeGender(String raw) { /* MALE/FEMALE/OTHER/UNKNOWN/null */ }
private LocalDate parseBirthday(String raw) { /* yyyy-MM-dd, 失败返回 null */ }
private String normalizeLanguage(String raw) { /* 原样清洗后返回，空值 null */ }
private String extractFirstName(String provider, Map<String, Object> attrs) { /* given_name/name 拆分 */ }
private String extractLastName(String provider, Map<String, Object> attrs) { /* family_name/name 拆分 */ }
```

- [ ] **Step 4: 编译 web 模块验证**

Run: `mvn -pl shopping-web -am -DskipTests compile`

Expected: `BUILD SUCCESS`

---

### Task 5: 集成验证（无新增测试前提下）

**Files:**
- Verify runtime behavior via existing OAuth flow

- [ ] **Step 1: 启动服务并走一次新用户 OAuth 登录**

Run: `mvn -pl shopping-web -am spring-boot:run`

Expected: 登录成功跳转仍为 `http://localhost:6655/login.html?<provider>=success`

- [ ] **Step 2: 检查数据库写入**

Run:

```sql
SELECT id, first_name, last_name, gender, bio, birthday, country, language, timezone
FROM user_profile
WHERE id = <oauth_user_id>;
```

Expected: 有且仅有 1 行；字段按 provider 映射，缺失为 `NULL`

- [ ] **Step 3: 重复同账号登录验证幂等**

Run: 再次同 provider 登录同账号

Expected: `user_profile` 不新增重复行（仍 1 行）

- [ ] **Step 4: 编译总验证**

Run: `mvn -DskipTests compile`

Expected: `BUILD SUCCESS`

---

## 实施注意事项
- 遵循当前项目偏好：本次不新增测试代码，仅做编译与流程验证。
- 不在本次 OAuth 流程更新 `avatar`、不主动写 `created_at/updated_at`。
- profile 初始化异常只记日志，不影响主登录成功。
