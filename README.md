# Shopping 风控购物系统

## 项目简介

Shopping 是一个基于 Spring Boot 的多模块购物系统项目。项目除了用户、商品、登录注册等基础能力外，重点实现了一套围绕账号安全的风控体系，覆盖预登录绑定、注册风控、登录风控、OAuth2 登录、短信验证码、TOTP、IP/设备风险画像、自动化防刷、锁号/封号和后台风控管理。

本文中的“分控”按项目当前实现统一理解为“风控”。当前风控重点是账号安全、认证链路和网络环境安全，不是商品、订单、库存、支付类交易风控。

README 不放图片和截图；需要更细的规则说明时，请查看 [docs/risk-control-system-overview.md](docs/risk-control-system-overview.md)。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 基础框架 | Java 21、Spring Boot 3.5.5、Maven 多模块 |
| Web 与安全 | Spring MVC、Spring Validation、Spring AOP、Spring Security、OAuth2 Client |
| 数据访问 | PostgreSQL、MyBatis、PageHelper、JPA、HikariCP |
| 缓存与脚本 | Redis、Lettuce、Redis Lua、Caffeine、Counting Bloom |
| 异步消息 | RabbitMQ、主队列、重试队列、死信队列 |
| 认证能力 | JWT / JJWT、TOTP、邮箱验证码、短信验证码 |
| 验证码 | Hutool Captcha、Tianai Captcha、Cloudflare Turnstile、hCaptcha、Google reCAPTCHA |
| IP 风险 | IP2Location.io、IP2Location 本地 BIN、iPing 降级查询 |
| 第三方服务 | GitHub OAuth2、Google OAuth2、Microsoft OAuth2 / Graph、QQ/Gmail/Outlook SMTP、阿里云短信、阿里云 OSS |
| 接口文档 | Springdoc OpenAPI、Knife4j |

## 模块结构

| 模块 | 主要职责 |
| --- | --- |
| `shopping-common` | 通用工具、Redis key、JWT、邮件、短信、阿里云 OSS、IP2Location 工具、分布式锁、本地代理、Counting Bloom 等基础能力。 |
| `shopping-model` | 实体模型、JPA/MyBatis 相关模型。 |
| `shopping-mapper` | MyBatis Mapper、数据库访问、分页查询。 |
| `shopping-service` | 用户认证、注册、登录、密码重置、验证码、短信、TOTP、账号风险、MQ 消费与发布等业务逻辑。 |
| `shopping-web` | 应用启动入口、Controller、Interceptor、Spring Security、后台管理、静态页面、IP 风险查询与写回。 |
| `sql` | 数据库建表和变更脚本。 |
| `docs` | 风控说明和设计文档。 |

## 核心功能

- 用户注册、登录、密码重置、账号注销。
- GitHub、Google、Microsoft 第三方 OAuth2 登录。
- 邮箱验证码、短信验证码、TOTP 二次验证。
- 用户头像上传和阿里云 OSS 对象存储。
- 管理员登录、首次登录验证、后台会话管理。
- 商品分类、商品 SPU、商品图片等商城基础管理能力。
- 风控后台：IP 风险分查询和批量调整、设备风险画像查询、验证码配置、风险 API 配置、IP2Location quota key 管理。

## 管理员后台操作与主从表关联

下面只列管理员后台直接查询、创建、更新、删除或恢复时涉及的主要数据表关系。Redis、Windows 环境变量、YAML 配置和 OSS 文件对象等非关系型存储不放入主从表关系表。

| 管理模块 | 主表 | 从表 / 关联表 | 关联字段 | 主要管理员操作 |
| --- | --- | --- | --- | --- |
| 商品分类管理 | `product_category` | `product_category` 子分类 | `product_category.parent_id -> product_category.id` | 分类树查询、新增分类、编辑分类、启用/禁用分类、批量禁用子树、删除叶子分类。 |
| 商品分类管理 | `product_category` | `product_spu` | `product_spu.category_id -> product_category.id` | 分类树统计商品数量；禁用分类前检查分类及子分类下是否存在启用商品。 |
| 商品 SPU 管理 | `product_spu` | `product_detail` | `product_detail.spu_id -> product_spu.id` | 新增/编辑商品详情，维护轮播图、详情图、属性、描述和售后信息。 |
| 商品 SPU 管理 | `product_spu` | `product_sku` | `product_sku.spu_id -> product_spu.id` | 新增/编辑商品规格、价格、库存、SKU 图片和 SKU 状态。 |
| 商品 SPU 管理 | `product_category` | `product_spu` | `product_spu.category_id -> product_category.id` | 商品分页筛选、创建商品、移动商品分类、按叶子分类批量禁用/删除商品。 |
| 账号信用管理 | `user_risk_profile` | `user_login_identity` | `user_login_identity.user_id -> user_risk_profile.user_id` | 按用户、邮箱、手机号、账号状态和风险等级查询账号信用。 |
| 账号信用管理 | `user_risk_profile` | `user_risk_score_event` | `user_risk_score_event.user_id -> user_risk_profile.user_id` | 查看账号风险分事件；管理员手动调整账号分数时写入事件流水。 |
| 账号信用管理 | `user_risk_profile` | `user_login_success_record` | `user_login_success_record.user_id -> user_risk_profile.user_id` | 查看账号详情时补充首次登录 IP、设备指纹和登录类型。 |
| 自助注销管理 | `user_account_self_deletion` | `user_login_identity` | `user_login_identity.user_id -> user_account_self_deletion.user_id` | 查询自助注销记录；7 天内恢复时把用户身份状态恢复为 `ACTIVE` 并写入恢复信息。 |
| 风控封号管理 | `user_risk_account_termination` | `user_login_identity` / `user_risk_profile` | `user_login_identity.user_id -> user_risk_account_termination.user_id`，`user_risk_profile.user_id -> user_risk_account_termination.user_id` | 查询风控封号列表和详情，展示账号状态、当前分数、风险等级和锁定次数。 |
| IP 风险分管理 | `ipv4_reputation_profile` / `ipv6_reputation_profile` | 无固定从表 | `ip` 为主键 | 按 IP、国家和风险等级分页查询 IPv4/IPv6 风险画像；批量调整 IP 当前分数。 |
| 设备风险画像管理 | `device_risk_profile` | `device_risk_score_event` | `device_risk_score_event.device_id -> device_risk_profile.id` | 查询设备风险列表、设备详情和设备扣分事件。 |
| 设备风险画像管理 | `device_risk_profile` | `device_user_relation` | `device_user_relation.device_id -> device_risk_profile.id` | 业务链路维护设备关联用户数，后台在设备画像中查看关联用户数量。 |

配置型后台操作不属于主从表关系：

- 管理员登录和后台会话：管理员账号来自 `config/admin.yaml`，后台会话存 Redis。
- 风险 API 配置、验证码配置、OAuth2 配置、SMTP 配置、OSS 配置、短信配置：主要用于读取和更新运行时配置或 Windows 环境变量。
- IP2Location quota key：存储在 Redis DB 2，key 前缀为 `ip2location:quota:`，不是数据库主从表。
- 商品图片预上传和取消：主要操作对象存储文件或预上传 URL，最终由 `product_spu`、`product_detail`、`product_sku` 中的图片 URL 字段引用。

## 风控系统概览

项目中的风控不是独立服务，而是嵌入注册、登录、OAuth2、短信、密码重置和登录态请求等关键链路。

| 风控能力 | 说明 |
| --- | --- |
| PreAuth 预登录绑定 | 在正式注册/登录前绑定 `PREAUTH_TOKEN`、设备指纹、UA、HTTP IP，并计算当前 IP 和设备风险。 |
| WebRTC 与 HTTP IP 一致性 | 前端上报 WebRTC 探测 IP，服务端对比 HTTP 出口 IP，用于识别疑似代理、VPN 或异常网络。 |
| IP 风险画像 | 通过 Caffeine、Redis、DB、IP2Location.io、iPing 等多级链路获取 IP 风险证据。 |
| 设备风险画像 | 记录设备分、关联用户数、IP 变化、地理跳跃、自动化命中、失败行为等。 |
| L1-L6 风险等级 | 分数越高风险越低，L1 低风险，L6 阻断级风险。 |
| 注册 challenge | 根据风险等级选择无挑战、Hutool、Tianai、Turnstile、hCaptcha、reCAPTCHA、OperationTimeout。 |
| 登录 challenge | 根据风险等级要求图形验证码、行为验证码、WAF_REQUIRED、OperationTimeout，并可要求多因子验证。 |
| 自动化防刷 | 使用 Redis Lua 在一次脚本中完成频率窗口计数、封禁判断和结果返回。 |
| 登录失败锁号 | 统计密码、邮箱 OTP、短信 OTP 等失败行为，超过阈值后扣分、锁定或风控封号。 |
| 登录后网络风险 | 登录态请求持续检测 IP 变化、国家变化、WebRTC 不一致、VPN/代理疑似、不可能旅行等事件。 |
| Counting Bloom | 用于 IP L6、设备 L6、封号邮箱、已绑定手机号、已手机号验证用户等快速预判集合。 |

详细规则、分数范围、Redis key 和主要数据表见 [docs/risk-control-system-overview.md](docs/risk-control-system-overview.md)。

## 外部 API 与第三方能力

下面列出项目中真正会调用外网的第三方 API 或服务。Hutool、Tianai、本地 IP2Location BIN 和内部 WAF 状态不归入外部 API。

| 类别 | 服务 | 默认地址 / 端点 | 项目用途 |
| --- | --- | --- | --- |
| IP 风险 | IP2Location.io | `https://api.ip2location.io/` | 查询 IP 欺诈分、代理、VPN、TOR、机房、地理位置、ASN 等风险证据。 |
| IP 风险降级 | iPing | `https://api.iping.cc/v1/query` | IP2Location 配额不足时的降级查询，目前主要用于 IPv4。 |
| 验证码 | Cloudflare Turnstile | `https://challenges.cloudflare.com/turnstile/v0/siteverify` | 校验 Turnstile token，用于注册、登录、短信等高风险场景。 |
| 验证码 | hCaptcha | `https://api.hcaptcha.com/siteverify` | 校验 hCaptcha token，用于注册、登录、短信等高风险场景。 |
| 验证码 | Google reCAPTCHA | `https://www.google.com/recaptcha/api/siteverify` | 校验 reCAPTCHA token，用于注册、登录、短信等高风险场景。 |
| OAuth2 登录 | GitHub OAuth2 | Spring Security 默认 GitHub provider | 第三方登录。 |
| OAuth2 登录 | Google OAuth2 | Spring Security 默认 Google provider | 第三方登录。 |
| OAuth2 登录 | Microsoft OAuth2 / Microsoft Graph | `https://login.microsoftonline.com/common/oauth2/v2.0/*`、`https://graph.microsoft.com/v1.0/me` | 第三方登录，必要时通过 Graph `/me` 兜底获取邮箱。 |
| 邮件 | QQ SMTP，后台也支持 Gmail / Outlook SMTP | `smtp.qq.com:587`、`smtp.gmail.com:587`、`smtp.office365.com:587` | 注册验证码、密码重置、欢迎邮件、账号通知。 |
| 短信 | 阿里云短信 | `dypnsapi.aliyuncs.com` | 手机号绑定、手机号登录、短信验证码。 |
| 文件存储 | 阿里云 OSS | `https://oss-cn-hongkong.aliyuncs.com` | 用户头像等对象存储。 |
| IP2Location 辅助工具 | Microsoft OAuth token + Outlook IMAP | `https://login.microsoftonline.com/common/oauth2/v2.0/token`、`imap-mail.outlook.com:993` | 读取 IP2Location 验证邮件和注册链接痕迹。 |

不是外部 API 的能力：

- Hutool 验证码：本地 Java 库生成和校验。
- Tianai Captcha：项目本地集成的行为验证码能力，包含滑块、旋转、拼接、文字点选。
- IP2Location BIN：本地离线库查询 IP 地理信息。
- `WAF_REQUIRED`：项目内部 challenge 状态和 `/shopping/auth/waf/verify` 回调，不是直接调用 Cloudflare WAF API。

本地 IP2Location BIN 数据库说明：

- 项目默认配置 `register.ip-country-cache.bin-path: IP2LOCATION-LITE-DB11.IPV6.BIN`，用于本地离线查询 IP 国家、地区、城市、经纬度、邮编和时区等地理信息。
- 该文件来源是 [IP2Location LITE DB11 IPv6 BIN](https://lite.ip2location.com/database/db11-ip-country-region-city-latitude-longitude-zipcode-timezone)，官方包名为 `DB11LITEBINIPV6`。这是 IP2Location LITE 数据库下载，不是 `ip2location.io` 在线 API。
- BIN 文件体积很大，不上传 GitHub。本仓库 `.gitignore` 已忽略 `IP2LOCATION-LITE-DB11.IPV6.BIN`，开发环境需要从官方页面手动下载后放到本地，或通过 `REGISTER_IP_COUNTRY_CACHE_BIN_PATH` 指向机器上的实际路径。
- LITE 数据库可免费用于个人或商业用途，但需要按 IP2Location 官方要求注明数据来源。

## 验证码与 WAF 挑战体系

项目把验证码和风控挑战统一抽象成 `challengeType` 和 `challengeSubType`。

| 挑战类型 | 说明 |
| --- | --- |
| `HUTOOL_SHEAR_CAPTCHA` | Hutool 本地图形/剪切验证码。 |
| `TIANAI_CAPTCHA` | Tianai 本地行为验证码，子类型包括 `SLIDER`、`ROTATE`、`CONCAT`、`WORD_IMAGE_CLICK`。 |
| `CLOUDFLARE_TURNSTILE` | Cloudflare Turnstile token 校验。 |
| `HCAPTCHA` | hCaptcha token 校验。 |
| `GOOGLE_RECAPTCHA_V2` | Google reCAPTCHA v2 校验。 |
| `GOOGLE_RECAPTCHA_V3` | 兼容旧 Redis challenge 值，当前主要保留为 legacy 值。 |
| `OPERATION_TIMEOUT` | 等待/阻断型挑战，不展示普通图形验证码。 |
| `WAF_REQUIRED` | 内部 WAF 验证流程，用于登录 L5、密码重置 L5、PreAuth IP 变化等场景。 |

注册链路会按 L1-L6 风险等级在无挑战、Hutool、Tianai、Turnstile、hCaptcha、reCAPTCHA、OperationTimeout 之间分流。登录链路会在中高风险时要求验证码或 WAF，并可能要求邮箱 OTP、TOTP、短信、手机号绑定等多因子验证。短信发送前也会进入风控 gate，避免短信接口被低成本刷爆。

完整分流规则见 [risk_challenge_rules.txt](risk_challenge_rules.txt)。

## 缓存、数据库与异步消息

典型 IP 风险查询链路：

```text
Caffeine 本地缓存
-> Redis 缓存
-> PostgreSQL 风险画像表
-> IP2Location.io
-> iPing 降级查询
-> Redis / DB / Counting Bloom 异步写回
```

主要存储能力：

- Redis：PreAuth binding、注册/登录 flow、验证码状态、短信限流、账号失败窗口、网络风险窗口、自动化防刷、IP/设备风险缓存、Counting Bloom、IP2Location quota。
- Caffeine：本地热点风险缓存，减少 Redis 和 DB 压力。
- PostgreSQL：用户身份、登录记录、用户风险画像、设备风险画像、IPv4/IPv6 风险画像、风险事件、封号记录、商品数据。
- RabbitMQ：注册邮箱验证码、密码重置邮件、欢迎邮件、短信验证码、头像上传、账号注销、IP 风险写回等异步任务。
- Redis Lua：用于自动化防刷、IP2Location quota 管理、批量扣减/补偿等需要原子性的场景。

项目约定集合查询链路优先走批量访问：Caffeine `getAllPresent`、Redis `multiGet/multiSet/delete(Collection)`、DB Mapper 集合参数和批量 SQL。

## 本地运行

基础环境：

- JDK 21
- Maven
- PostgreSQL
- Redis
- RabbitMQ
- 本地 IP2Location LITE DB11 IPv6 BIN 文件，默认文件名为 `IP2LOCATION-LITE-DB11.IPV6.BIN`，文件较大，不提交到 GitHub。
- 可选：本地代理、阿里云 OSS/短信、OAuth2 应用、第三方验证码站点密钥

常用启动命令：

```bash
mvn -pl shopping-web -am spring-boot:run
```

默认配置：

- 应用名：`shopping`
- HTTPS 端口：`6655`
- 启动类：`shopping-web/src/main/java/com/example/ShoppingSystem/ShoppingSystemApplication.java`
- 主配置：`shopping-web/src/main/resources/application.yaml`

常见环境变量：

| 变量 | 用途 |
| --- | --- |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth2 登录。 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 登录。 |
| `AZURE_CLIENT_ID` / `AZURE_CLIENT_SECRET` | Microsoft OAuth2 登录。 |
| `EMAIL_SMTP_USERNAME` / `EMAIL_SMTP_PASSWORD` | 邮件发送账号。 |
| `TURNSTILE_SITE_KEY` / `TURNSTILE_SECRET_KEY` | Cloudflare Turnstile。 |
| `HCAPTCHA_SITE_KEY` / `HCAPTCHA_SECRET_KEY` | hCaptcha。 |
| `RECAPTCHA_SITE_KEY` / `RECAPTCHA_SECRET_KEY` | Google reCAPTCHA。 |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS。 |
| `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET` | 阿里云短信。 |
| `IP2LOCATION_IO_API_URL` | IP2Location.io API 地址（只控制外部请求地址，不是 Redis key）。 |
| `IPING_API_ENABLED` / `IPING_API_URL` / `IPING_API_LANGUAGE` | iPing 降级查询。 |

不要把真实密钥提交到仓库。

IP2Location 配额 key 说明：

- `IP2LOCATION_IO_API_URL` 只配置 `https://api.ip2location.io/` 这类请求地址；真正存入 Redis 的是配额 key。
- Redis 配额主前缀是 `ip2location:quota:`，完整格式为 `ip2location:quota:{accountType}:{yyyy-MM-dd-HH:mm}:{apiKey}`。
- 计数总和 key 是 `ip2location:quota:count`，轮询游标 key 是 `ip2location:round-robin:cursor`。
- `AccountType.FREE` 作为试用 key，TTL 为 7 天；`STARTER`、`PLUS`、`SECURITY` 的 TTL 为 30 天。
- 本项目的使用机制是：后台可以批量添加/删除 quota key，查询时按轮询从仍有剩余额度的 key 中取一个，调用失败时会回补额度；当 IP2Location 配额不足时，风控查询链路会降级到 iPing。

## 文档索引

- [docs/risk-control-system-overview.md](docs/risk-control-system-overview.md)：风控系统实现总览。
- [risk_challenge_rules.txt](risk_challenge_rules.txt)：注册、登录、短信、密码重置等 challenge 分流规则。
- [bot_attack_defense_rules.txt](bot_attack_defense_rules.txt)：自动化攻击和防刷规则说明。
- [post_login_account_network_ip_risk_plan.txt](post_login_account_network_ip_risk_plan.txt)：登录后账号网络/IP 风险方案。
- [ip_change_device_score_penalty_design.txt](ip_change_device_score_penalty_design.txt)：IP 变化与设备分扣减设计。
- [ip_reputation_reference_score_formula.txt](ip_reputation_reference_score_formula.txt)：IP 信誉分参考公式。
