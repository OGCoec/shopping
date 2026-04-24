# Google OAuth 登录接入设计

## 目标
在当前 shopping 项目中新增一套独立的 Google OAuth 登录后端接口，复用现有项目的身份表与认证基础设施，使用户访问后端接口即可完成 Google 授权、身份绑定/创建、本地登录态建立与站内跳转。

本次设计明确不替换现有 GitHub 登录实现，不要求先改造前端页面，重点是让 Google 登录接口在后端完整可用。

## 范围
### 包含
- 新增 Google 登录发起接口
- 新增 Google 登录回调接口
- 新增 Google state 的 Redis 存储键定义
- 新增 Google 身份绑定/创建服务
- 回调成功后直接建立本地登录态
- 补充安全放行与配置项

### 不包含
- 删除现有 GitHub 登录流程
- 重构为统一的多平台 OAuth 抽象层
- 大规模改造前端登录页
- 引入外部项目中的 shortToken / mediumToken 双令牌体系

## 背景与约束
当前项目已有 GitHub OAuth 登录流程，核心文件包括：
- `src/main/java/com/example/ShoppingSystem/controller/GithubAuthController.java`
- `src/main/java/com/example/ShoppingSystem/service/GithubAuthService.java`
- `src/main/java/com/example/ShoppingSystem/redisdata/GithubRedisKeys.java`

现有 GitHub 流程已经实现：
- 发起 OAuth 授权
- Redis state 校验
- 换取 access token
- 拉取第三方用户信息
- 在本地身份表中绑定或创建 `UserLoginIdentity`

但当前 GitHub 流程没有在回调成功后显式建立完整的本地登录态，因此本次 Google 接入不能只是照搬 GitHub 的“身份绑定”逻辑，还需要把“登录成功后的本地认证落地”一并补齐。

## 方案对比

### 方案 A：新增独立 Google OAuth 链路（推荐）
新增 `GoogleAuthController`、`GoogleAuthService`、`GoogleRedisKeys`，整体参考现有 GitHub 实现与外部项目的 `GoogleAuthController`，但登录态部分优先复用本项目已有认证工具而不是照搬外部项目 token 体系。

**优点：**
- 风险最小，不影响现有 GitHub 登录
- 结构清晰，便于对照已有实现开发
- 最符合“新增后端接口，直接可用”的目标

**缺点：**
- 会有一定代码重复
- 后续若再接更多平台，可能需要再抽象

### 方案 B：把 GitHub 控制器扩展成通用 OAuth 控制器
把 GitHub 与 Google 合并到一个控制器或统一服务中。

**优点：**
- 表面上更统一

**缺点：**
- 改动面更大
- 容易影响现有已工作的 GitHub 登录
- 当前阶段收益小于风险

### 方案 C：仅新增 Google 身份绑定，不建立登录态
只把 Google 身份写入本地身份表，回调后跳回登录页。

**优点：**
- 实现最快

**缺点：**
- 不满足“Google 成功后直接完成登录”的目标

## 最终设计
采用**方案 A**：新增独立 Google OAuth 链路，并在回调成功后直接建立本地登录态。

## 架构设计

### 1. 新增控制器：`GoogleAuthController`
职责：
- 提供 `GET /oauth2/google/login`
- 提供 `GET /login/google/callback`
- 管理 OAuth 协议交互流程
- 调用服务层完成身份绑定/创建
- 在认证成功后写入本地登录态并重定向

控制器只处理协议流程，不承载复杂持久化逻辑。

### 2. 新增服务：`GoogleAuthService`
职责：
- 按 `googleId` 查询是否已有本地身份
- 若不存在且有邮箱，则尝试按邮箱查找现有身份并绑定 `google_id`
- 若仍不存在，则创建新的 `UserLoginIdentity`
- 返回可用于建立登录态的本地身份对象

该服务的设计与 `GithubAuthService` 保持同一风格，降低接入成本。

### 3. 新增 Redis 键定义：`GoogleRedisKeys`
职责：
- 定义 Google OAuth 的 state key 前缀
- 定义 state TTL

命名风格与 `GithubRedisKeys` 保持一致，例如：
- `STATE_PREFIX = "auth:google:state:"`
- `STATE_TTL_MINUTES = 5L`

### 4. 登录态建立逻辑
本次不照搬外部项目中的 `shortToken/mediumToken` 双 token 设计。

改为：
- 优先复用项目现有 `JwtUtils` 或现有认证基础设施生成本地登录凭证
- 通过 Cookie 写回浏览器
- 回调成功后重定向到站内已登录页面

这样既满足“直接完成登录”，又保持与当前项目风格一致，避免引入不必要的复杂 token 体系。

如果在实现阶段确认当前项目还缺少真正可用的本地登录令牌写入流程，则计划中应补一个最小可用方案：
- 由后端生成登录 token
- 使用 HttpOnly Cookie 写入
- 后续请求通过既有认证链路识别该 token

## 数据流

### 入口：`GET /oauth2/google/login`
1. 生成高强度随机 `state`
2. 以 `auth:google:state:<state>` 形式写入 Redis
3. 设置 5 分钟过期
4. 组装 Google OAuth 授权地址
5. 将浏览器重定向到 Google 授权页

请求参数至少包含：
- `client_id`
- `redirect_uri`
- `response_type=code`
- `scope=openid profile email`
- `state`

### 回调：`GET /login/google/callback`
1. 读取 `code`、`state`、`error`
2. 校验 `state` 是否存在且合法
3. 删除已消费的 state，避免重放
4. 如果授权失败或 `code` 为空，则跳转失败页
5. 使用 `code` 请求 Google token 接口换取 access token
6. 使用 access token 调用 Google 用户信息接口
7. 提取 Google 用户标识与基本资料
8. 调用 `googleAuthService.loginByGoogle(...)`
9. 根据返回的本地身份建立本地登录态
10. 写入 Cookie
11. 跳转到成功页或站内目标页

## 第三方字段映射
Google 用户信息建议使用如下字段：
- `sub` -> 本地 `googleId`
- `email` -> 本地邮箱候选值
- `name` -> 可选展示名
- `picture` -> 可选头像

其中真正参与身份绑定的关键字段是：
- `sub`
- `email`

`name` 与 `picture` 仅在未来需要扩展用户资料时作为附加信息使用，本次不作为必要持久化目标。

## 本地身份绑定规则
`GoogleAuthService` 采用与 `GithubAuthService` 相同的判定顺序：

1. **先按 `googleId` 查本地身份**
   - 如果存在：更新登录时间并直接返回

2. **如果没有 `googleId` 记录，但拿到了邮箱**
   - 把邮箱标准化为小写、去首尾空格
   - 按邮箱查本地身份
   - 如果存在：把 `google_id` 绑定到该身份并返回

3. **如果邮箱也找不到现有身份**
   - 创建新的 `UserLoginIdentity`
   - 填充必要字段
   - 写入 `google_id`
   - 返回新身份

4. **如果 Google 没有返回邮箱**
   - 仍允许基于 `googleId` 创建最小身份记录

## 本地登录态设计
本次设计要求 Google 登录成功后“直接完成登录”，因此不能只返回一个身份对象。

建议的最小落地方案：
- 成功获取本地身份后，生成项目本地认证 token
- 使用 HttpOnly Cookie 写回浏览器
- 设置合理的 Path、Max-Age、是否 Secure 等属性
- 跳转到登录成功页或主页

Cookie 设计遵循以下原则：
- 凭证 Cookie 使用 `HttpOnly`
- 非必要不暴露敏感 token 给前端脚本
- Cookie 名称尽量遵循项目现有命名方式

如果当前系统已有现成登录 cookie 命名约定，则应优先复用，不新增风格不同的命名体系。

## 配置设计
需要在 `application.yaml` 中补充或确认：
- `spring.security.oauth2.client.registration.google.client-id`
- `spring.security.oauth2.client.registration.google.client-secret`
- `app.google.redirect-uri`

建议与 GitHub 保持类似风格，例如：
```yaml
app:
  github:
    redirect-uri: http://localhost:6655/login/github/callback
  google:
    redirect-uri: http://localhost:6655/login/google/callback
```

## 安全设计
### state 防重放
- state 存 Redis
- 回调时验证
- 验证成功后立即删除

### 最小授权范围
Google 授权范围仅使用：
- `openid`
- `profile`
- `email`

### 失败处理
失败场景包括：
- `state` 缺失
- `state` 无效或过期
- 用户取消授权
- token 获取失败
- 用户信息获取失败
- 本地身份绑定失败
- 本地登录态建立失败

所有失败都应统一跳转到项目可接受的失败地址，并附带简洁错误标识，避免直接暴露异常细节。

## 页面与跳转策略
当前设计不依赖前端登录页改造也能使用，因此即使没有新增 Google 按钮，只要直接访问：
- `/oauth2/google/login`

也应能跑通完整流程。

成功跳转目标建议先复用当前登录成功页或主页，失败跳转目标建议复用当前登录页并附带错误参数。

## 文件变更清单
### 新增
- `src/main/java/com/example/ShoppingSystem/controller/GoogleAuthController.java`
- `src/main/java/com/example/ShoppingSystem/service/GoogleAuthService.java`
- `src/main/java/com/example/ShoppingSystem/redisdata/GoogleRedisKeys.java`

### 修改
- `src/main/java/com/example/ShoppingSystem/config/SecurityConfig.java`
- `src/main/resources/application.yaml`

### 可能复用
- `src/main/java/com/example/ShoppingSystem/service/GithubAuthService.java`
- `src/main/java/com/example/ShoppingSystem/mapper/UserLoginIdentityMapper.java`
- `src/main/java/com/example/ShoppingSystem/entity/entity/UserLoginIdentity.java`
- `src/main/java/com/example/ShoppingSystem/Utils/JwtUtils.java`

## 测试设计
至少覆盖以下验证点：

### 功能验证
1. 访问 `/oauth2/google/login` 能成功跳转 Google 授权页
2. 回调带合法 `state` 时能继续流程
3. 非法或过期 `state` 会被拒绝
4. 首次 Google 登录可创建本地身份
5. 已有邮箱用户可绑定 `google_id`
6. 已绑定 `google_id` 的用户可重复登录
7. 登录成功后浏览器收到本地认证 Cookie
8. 成功跳转后的页面能被识别为已登录状态

### 异常验证
1. 缺少 `code`
2. Google 返回 `error`
3. token 接口失败
4. userinfo 接口失败
5. 本地身份创建失败
6. 本地登录 token 写入失败

## 可维护性要求
- 新增代码注释使用简体中文
- 控制器保持“协议处理”职责，业务逻辑放到 service
- 尽量沿用现有 GitHub 登录代码风格
- 不做与当前目标无关的抽象与重构

## 实施结论
本次实施应把外部项目 `GoogleAuthController` 的核心 OAuth 思路迁移到当前项目，但不机械照搬其完整实现。

应保留的部分：
- OAuth2 授权码流程
- Redis state 校验
- code 换 token
- access token 拉用户信息
- 回调后直接登录

应适配当前项目的部分：
- 本地身份表结构
- 登录态生成方式
- Cookie 命名与站内跳转
- Spring Security 放行规则

最终目标是：**用户访问本项目的 Google 登录接口后，无需依赖前端特殊逻辑，即可完成 Google 授权、本地身份落库/绑定、本地登录态建立与页面跳转。**
