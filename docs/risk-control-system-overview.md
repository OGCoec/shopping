# 风控系统实现总览

本文档整理当前项目中的“风控/分控”实现。这里的“分控”按项目实际代码理解为“风控”：以账号安全为主，覆盖预登录、注册、登录、OAuth2、短信验证码、TOTP、登录后网络变化、IP/设备画像、锁号/封号和后台管理。

## 1. 总体链路

当前风控不是一个单独的服务，而是分散在多个入口和业务链路中：

| 场景 | 主要入口 | 主要职责 |
| --- | --- | --- |
| WebRTC 与 HTTP 出口一致性 | `WebRtcIpConsistencyInterceptor` / `WebRtcIpConsistencyService` | 判断浏览器 WebRTC 探测到的公网 IP 与服务端看到的 HTTP IP 是否一致，不一致时拦截或记录风险。 |
| 预登录绑定 | `PreAuthInterceptor` / `PreAuthBindingService` | 绑定 `PREAUTH_TOKEN`、设备指纹、UA、当前 HTTP IP，并计算 IP/设备风险等级。 |
| 注册风控 | `RegisterController` / `RegisterPrecheckServiceImpl` | 注册前置检查、封号邮箱拦截、验证码挑战、邮箱验证码冷却、手机号绑定要求。 |
| 登录风控 | `LoginController` / `UserPasswordLoginServiceImpl` | 登录开始、密码/邮箱 OTP/TOTP/短信校验、风险 challenge、二次验证、手机号绑定。 |
| OAuth2 风控 | `OAuth2PreAuthRiskFilter` / `OAuth2PreAuthRiskGuard` | OAuth2 跳转前检查 PreAuth 绑定和风险等级，低分或 L6 阻断。 |
| 登录后网络风控 | `PostLoginAccountNetworkRiskInterceptor` / `AccountNetworkRiskService` | 登录态请求中检测 IP 变化、国家变化、WebRTC 不一致、疑似 VPN/代理、异常地理跳跃。 |
| 自动化防刷 | `AutomationRiskGateServiceImpl` + Lua 脚本 | 对注册/登录开始、未知登录标识做设备/IP 频率窗口计数，超限后封禁并扣分。 |
| 后台管理 | `AdminRiskCreditScoreController` / `AdminRiskApiConfigController` | 查询/调整 IP 和设备风险分，管理 IP2Location API key 配额与风控 API 配置。 |

## 2. WebRTC IP 与 HTTP IP 的 VPN/代理判断

项目里关于 VPN/代理最直接的一条判断链路是 `webRtcIps` 和 `httpIp` 的关系，不是单纯依赖第三方 IP 库。

### 2.1 输入来源

`httpIp` 由 `PreAuthRequestResolver.resolveClientIp` 解析，优先级是：

1. `X-Forwarded-For` 的第一个 IP
2. `X-Real-IP`
3. `request.getRemoteAddr()`

`webRtcIps` 由前端通过以下 Header 上报：

| Header | 含义 |
| --- | --- |
| `X-WebRTC-IP` | WebRTC 探测到的主 IP。 |
| `X-WebRTC-IPs` | WebRTC 探测到的候选 IP 列表，支持逗号或空白分隔。 |
| `X-WebRTC-Status` | 探测状态，当前 `ok` 表示拿到了可验证的公网 IP 信号。 |

### 2.2 判定规则

`WebRtcIpConsistencyService` 的核心逻辑是：

```text
webRtcStatus == ok
AND webRtcIps 非空
AND httpIp 非空
AND httpIp 不在 webRtcIps 中
AND httpIp 与 webRtcIps 不命中 trustedExitIpGroups
=> WEBRTC_IP_MISMATCH
```

命中 `WEBRTC_IP_MISMATCH` 时：

- HTML 导航请求会被重定向到 `/shopping/auth/network-check-failed`。
- API 请求返回 `403` JSON，错误码为 `WEBRTC_IP_MISMATCH`。
- PreAuth binding 会保存本次 `webRtcIp`、`webRtcStatus`、`webRtcMismatchCount`。

### 2.3 trustedExitIpGroups 例外

`TrustedExitIpMatcher` 支持可信出口 IP 组。只要 `httpIp` 和任意 `webRtcIp` 出现在同一个组里，就不认为是不一致。

该配置用于处理合法的双出口、Cloudflare/隧道、代理回源等场景，避免把已知可信链路误判为 VPN。

### 2.4 登录后的二次确认

登录态请求中，`AccountNetworkRiskService` 会再次使用 WebRTC 与 HTTP IP 的关系：

```text
webRtcStatus == ok
AND httpIp 不在 webRtcIps
AND 不命中 trustedExitIpGroups
=> 增加 WEBRTC_MISMATCH 风控事件
```

如果同时满足 `ipChanged || webRtcMismatch`，系统还会查询 IP 信誉链路：

```text
IP 信誉证据包含 VPN/TOR/公共代理/Web 代理/机房/住宅代理/隐私网络/企业专网标记
OR proxyType 属于 VPN/TOR/PUB/WEB/DCH/RES/CPN/EPN
OR currentScore < 4800
=> 增加 VPN_PROXY_SUSPECTED 风控事件
```

因此项目中更准确的表达是：

```text
WebRTC IP 与 HTTP IP 不一致 => 网络环境异常或疑似代理
WebRTC 不一致 + IP 信誉也异常 => VPN_PROXY_SUSPECTED
```

## 3. IP 与设备风险评分

### 3.1 IP 风险分

IP 风险数据由 `IpReputationMultiLevelQueryService` 查询，顺序是：

```text
Caffeine 本地缓存
-> Redis 缓存
-> DB：ipv4_reputation_profile / ipv6_reputation_profile
-> 外部 API：IP2Location.io
-> IP2Location quota 不足时 fallback 到 iPing
```

IP 风险证据统一成 `IpReputationEvidence`，主要字段包括：

- `fraudScore`
- `usageType`
- `proxyType`
- `proxyIsVpn`
- `proxyIsTor`
- `proxyIsPublicProxy`
- `proxyIsWebProxy`
- `proxyIsDataCenter`
- `proxyIsResidentialProxy`
- `proxyIsConsumerPrivacyNetwork`
- `proxyIsEnterprisePrivateNetwork`
- `asUsageType`
- `addressType`

评分公式核心是：分数越高风险越低。VPN、代理、TOR、机房、欺诈分越高，扣分越多；住宅/移动网络且无代理标记时会加可信分。

### 3.2 设备风险分

设备风险由 `DeviceRiskMultiLevelQueryService` 和 `DeviceRiskProfileWriteService` 维护，主要落在 `device_risk_profile`。

设备风险会受到以下行为影响：

- 同设备关联多个用户。
- 短时间或长期 IP 频繁变化。
- IP 地理位置变化过快。
- PreAuth 阶段 IP 变化触发 WAF。
- 自动化防刷命中设备维度封禁。

设备分低于 L6 阈值时会同步到设备 L6 Counting Bloom，后续可快速命中阻断。

### 3.3 综合风险分

注册、登录和 PreAuth 使用相同的综合思路：

```text
lowScore = min(ipScore, deviceScore)
highScore = max(ipScore, deviceScore)

如果 IP 或设备命中 L6 Counting Bloom：
  totalScore = lowScore
  riskLevel = L6
否则：
  totalScore = round(lowScore * 0.8 + highScore * 0.2)
  riskLevel = 按阈值映射
```

风险等级阈值由 `ChallengePolicy.resolveRiskLevel` 定义：

| 风险等级 | 分数范围 | 含义 |
| --- | --- | --- |
| `L1` | `score >= 8500` | 低风险。 |
| `L2` | `7500 <= score < 8500` | 较低风险。 |
| `L3` | `6000 <= score < 7500` | 中风险。 |
| `L4` | `4800 <= score < 6000` | 较高风险。 |
| `L5` | `3000 <= score < 4800` | 高风险。 |
| `L6` | `score < 3000` | 阻断级别风险。 |

## 4. 注册与登录 challenge 策略

### 4.1 注册 challenge

注册链路由 `RegisterPrecheckServiceImpl` 编排，风险等级对应的 challenge 由 `ChallengePolicy` 决定：

| 风险等级 | 注册 challenge 策略 |
| --- | --- |
| `L1` | 不挑战。 |
| `L2` | 50% 不挑战，25% Hutool，25% Tianai。 |
| `L3` | 1/3 Hutool，2/3 Tianai；Tianai 包含 slider、rotate、concat、word-click。 |
| `L4` | Turnstile、hCaptcha、reCAPTCHA v2、Tianai 随机分流。 |
| `L5` | Turnstile、hCaptcha、reCAPTCHA v2、OperationTimeout 随机分流，其中 OperationTimeout 占比更高。 |
| `L6` | OperationTimeout；PreAuth 层通常已经阻断 L6。 |

注册还会做：

- 封号邮箱 Counting Bloom 拦截。
- 邮箱验证码冷却。
- pending challenge Redis 会话。
- L3/L4/L5 注册成功前后要求手机号绑定。

### 4.2 登录 challenge 和二次验证

登录链路由 `UserPasswordLoginServiceImpl` 编排，`LoginChallengePolicy` 决定登录 challenge：

| 风险等级 | 登录 challenge 策略 | 二次验证 |
| --- | --- | --- |
| `L1` | 不挑战。 | 1 个 factor。 |
| `L2` | Hutool。 | 1 个 factor。 |
| `L3` | Tianai 随机子类型。 | 需要 2 个 factor。 |
| `L4` | Turnstile、hCaptcha、reCAPTCHA v2 按邮箱和设备指纹稳定分桶。 | 需要 2 个 factor。 |
| `L5` | WAF_REQUIRED。 | 需要 2 个 factor。 |
| `L6` | OperationTimeout。 | 阻断级别，通常不能正常继续。 |

登录 factor 包括密码、邮箱验证码、TOTP、短信验证码、手机号绑定等。密码、邮箱 OTP、短信 OTP 失败会进入账号失败风控。

## 5. 自动化防刷、锁号与封号

### 5.1 自动化防刷

`AutomationRiskGateServiceImpl` 使用 Redis Lua 脚本一次完成计数、封禁判断和返回结果。

| 脚本 | 场景 | 计数维度 |
| --- | --- | --- |
| `automation_start_gate.lua` | 注册/登录开始 | 设备 1s/1m/30m，IP 1s/1m/30m。 |
| `automation_unknown_login_gate.lua` | 未知登录标识 | 设备 30m，IP 30m。 |

命中封禁后会写入设备/IP block key，并对设备画像或 IP 画像扣分。

### 5.2 登录失败锁号

`UserAuthFailureRiskServiceImpl` 维护账号失败窗口：

- 单类失败超过 8 次会触发锁定。
- 总失败超过 15 次会触发锁定。
- 第 1 次锁 1 天并扣 600 分。
- 第 2 次锁 3 天并扣 1000 分。
- 第 3 次锁 7 天并扣 1500 分。
- 第 4 次及以后进入 `ACCOUNT_TERMINATION_REQUIRED`，账号状态变为 `RISK_TERMINATED`。

锁号状态同时写 Redis 锁 key、`user_risk_profile` 和 `user_risk_score_event`。

### 5.3 登录后网络风险锁号

`AccountNetworkRiskService` 登录后持续观察账号网络环境：

| 风控事件 | 触发条件 | 分值 |
| --- | --- | --- |
| `WEBRTC_IP_MISMATCH` | WebRTC IP 与 HTTP IP 不一致。 | 100 |
| `VPN_PROXY_SUSPECTED` | WebRTC/IP 变化后，IP 信誉显示 VPN/代理/机房/低分。 | 80 |
| `ACCOUNT_IP_CHANGED` | 当前 HTTP IP 与上次登录 IP 不同。 | 40 |
| `COUNTRY_CHANGED` | IP 国家变化。 | 120 |
| `IMPOSSIBLE_TRAVEL` | 依据地理距离和时间窗口判断不可能旅行。 | 动态分值。 |

30 分钟网络风险累计分超过阈值后，会锁定账号或进一步触发风控封号。

## 6. 短信、TOTP 与手机号相关风控

### 6.1 短信验证码

`PhoneSmsRiskGateServiceImpl` 在短信发送前根据风险等级要求验证码：

- `L6` 直接拒绝，不允许继续消耗短信资源。
- `L1/L2` 也要求轻量验证码，防止短信接口被低成本刷。
- `L3/L4/L5` 使用更强的 Turnstile、hCaptcha、reCAPTCHA 等 challenge。

`SmsCodeServiceImpl` 负责短信发送和校验限流：

- 手机号发送冷却。
- 手机号日限。
- IP 小时窗口限流。
- 验证码哈希存 Redis，校验成功后删除 code 和 cooldown key。

### 6.2 TOTP

`UserTotpServiceImpl` 负责 TOTP 设置和校验：

- 设置阶段生成 secret，并要求用户用验证码确认。
- 登录校验时校验 TOTP code。
- 使用 `lastUsedStep` 防止同一个 TOTP 时间步重复使用。

### 6.3 手机号 Counting Bloom

手机号相关 Counting Bloom 用于快速判断：

- 已绑定手机号是否可能存在。
- 用户是否已经通过手机号验证。

Bloom 命中不是最终事实，必要时仍会回查 DB 或 Redis 缓存。

## 7. 数据存储与缓存

### 7.1 Redis

主要 Redis 用途：

- PreAuth binding：`register:preauth:bind:*`
- 注册 flow：`auth:register:flow:*`
- 注册 challenge：`auth:register:challenge:*`
- 登录 challenge：`auth:login:challenge:*`
- 登录 WAF 验证票据：`auth:login:waf-verified:*`
- 注册/登录邮箱验证码和冷却 key
- 短信验证码、冷却、手机号日限、IP 小时限流 key
- 账号失败 30 分钟窗口：`risk:user:{userId}:window:30m:auth:*`
- 登录后网络风险 30 分钟窗口：`risk:user:{userId}:window:30m:network:*`
- 自动化防刷 block/rate key
- IP 风险缓存：`register:ip:risk:v2:*`
- 设备风险缓存：`register:device:risk:v2:*`
- IP 地理缓存：`register:ip:country:*`
- IP2Location quota key 和总配额计数

### 7.2 Caffeine / Redis / DB 多级缓存

IP 风险查询链路：

```text
Caffeine get
-> Redis get
-> DB 查询 ipv4/ipv6 reputation profile
-> IP2Location API
-> quota 不足时 iPing fallback
-> 异步写回 DB / Redis / Bloom
```

设备风险查询链路：

```text
设备 L6 Counting Bloom 快速判定
-> Caffeine 本地缓存
-> Redis 缓存
-> DB device_risk_profile
-> 缺失时初始化设备画像
```

### 7.3 Counting Bloom

项目中使用 Counting Bloom 快速判断高频风险集合：

| 集合 | 用途 |
| --- | --- |
| IP L6 Counting Bloom | IP 分低于 3000 时快速判定 L6。 |
| 设备 L6 Counting Bloom | 设备分低于 3000 时快速判定 L6。 |
| 风控封号邮箱 Counting Bloom | 注册/登录时快速拦截已风控封号邮箱。 |
| 已绑定手机号 Counting Bloom | 手机号登录/绑定可用性预判。 |
| 已手机号验证用户 Counting Bloom | 判断用户是否已经完成手机号验证。 |

### 7.4 DB 表

主要风险表：

| 表 | 作用 |
| --- | --- |
| `user_risk_profile` | 用户当前风险画像、当前分、风险等级、锁定状态、最近登录 IP/设备。 |
| `device_risk_profile` | 设备当前风险画像、设备分、风险等级、历史 IP、关联用户数、最近扣分信息。 |
| `ipv4_reputation_profile` | IPv4 风险画像、第三方数据、当前分、缓存过期时间。 |
| `ipv6_reputation_profile` | IPv6 风险画像、第三方数据、当前分、缓存过期时间。 |
| `user_risk_score_event` | 用户风险分变更流水。 |
| `device_risk_score_event` | 设备风险扣分流水。 |
| `user_risk_account_termination` | 风控强制注销/封号拦截表，保存邮箱和手机号 hash。 |
| `user_login_success_record` | 登录成功记录。 |
| `user_login_fail_record` | 登录失败记录。 |

## 8. 后台风控管理

后台风控能力主要包括：

- `AdminRiskCreditScoreController`：查询 IP 风险画像、设备风险画像，批量调整 IP 分数。
- `AdminRiskCreditScoreService`：按 `L1-L6` 分数区间分页查询，批量更新 IP 分数后清理缓存并同步 IP L6 Counting Bloom。
- `AdminRiskApiConfigController`：管理风险 API 配置、IP2Location quota key、批量添加/删除 quota key。
- `Ip2LocationQuotaService`：用 Redis Lua 管理 IP2Location API key 的配额、轮询、扣减、补偿、批量新增和批量删除。

## 9. 实现边界

- 当前文档只描述现有实现，不新增 API，不修改 Java 类型，不修改 Redis key，不修改 SQL。
- 当前风控重点是账号安全、认证链路和网络环境安全，不是商品、订单、支付、库存类交易风控。
- 代码中部分中文注释存在编码显示问题，本文档按实际类名、方法名、事件名和当前逻辑整理，不重写源码注释。
