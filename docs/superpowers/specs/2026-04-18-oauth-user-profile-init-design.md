# OAuth 首次登录初始化 user_profile 设计

## 目标
在 Google / Microsoft / GitHub OAuth 登录成功后，基于 provider 返回属性初始化 `user_profile`。

约束：
- 仅在 `user_profile` 不存在时初始化（`id = user_id`）。
- 字段按 provider 原始属性“尽量映射，拿不到则 NULL”。
- 不在该流程更新头像与 `updated_at`（后续接口负责）。
- 不影响主登录成功链路。

## 现状
- OAuth 成功入口：`shopping-web/src/main/java/com/example/ShoppingSystem/security/OAuth2LoginSuccessHandler.java`
- 当前仅处理 `user_login_identity` 的查询/绑定/创建。
- `user_profile` 表存在（`sql/002_create_user_profile.sql`），但当前 OAuth 登录后未初始化。

## 方案概览（已选）
在 `OAuth2LoginSuccessHandler` 聚合 provider attrs 生成 profile draft，并调用统一服务 `UserProfileService.initIfAbsent(userId, draft)`。

## 变更范围
1. 新增 `UserProfile` 实体（若项目中不存在）。
2. 新增 `UserProfileMapper` 与 XML：
   - `existsById(Long id)`
   - `insertUserProfile(UserProfile profile)`
3. 新增 `UserProfileService`：
   - `initIfAbsent(Long userId, UserProfileDraft draft)`
4. 修改 `OAuth2LoginSuccessHandler`：
   - 在身份创建/绑定成功后，提取 provider attrs 并调用 profile 初始化。

## 数据流
1. OAuth 成功回调进入 `OAuth2LoginSuccessHandler`。
2. 现有逻辑完成 `user_login_identity` 登录绑定，得到 `identity.userId`。
3. SuccessHandler 根据 `registrationId + attrs` 构造 `UserProfileDraft`。
4. 调用 `userProfileService.initIfAbsent(identity.userId, draft)`：
   - 已存在：直接返回，不写库。
   - 不存在：插入一条 profile。
5. 保持现有登录成功重定向行为。

## 字段映射规则
### first_name / last_name
- 优先使用 provider 明确字段：
  - Google: `given_name` / `family_name`
  - Microsoft / GitHub: 若存在同义字段优先使用
- 若无明确字段，使用 `name` 做拆分：
  - 两段及以上：最后一段给 `last_name`，其余合并为 `first_name`
  - 单段：写 `first_name`，`last_name = NULL`
- 都无：`NULL`

### gender
- 读取 provider 原始值并归一化：`male/female/other/unknown` → `MALE/FEMALE/OTHER/UNKNOWN`
- 无法识别或不存在：`NULL`

### bio
- 读取 provider 描述字段（如 `bio`, `description` 等）
- 无：`NULL`

### birthday
- 尝试解析 `yyyy-MM-dd`（可扩展常见格式）
- 解析失败或不存在：`NULL`

### country
- 读取 `country` / `locale` 推断国家（仅在明确可映射时写）
- 否则：`NULL`

### language
- 优先读 locale 语言值（如 `zh-CN`, `en-US`）
- 不存在：`NULL`

### timezone
- 读取 provider 时区字段（若存在并合法）
- 不存在：`NULL`

## 时间字段策略
- `created_at` / `updated_at` 依赖数据库默认值 `NOW()`。
- 本流程不显式更新 `updated_at`。

## 异常与容错
- `user_profile` 初始化失败时：
  - 记录错误日志（含 provider、userId、错误摘要）
  - 不中断 OAuth 主登录成功流程（保持成功跳转）

## SQL/Mapper 约束
- 插入字段仅包含：
  - `id, first_name, last_name, gender, bio, birthday, country, language, timezone`
- 不写入 `avatar, created_at, updated_at`。

## 验证用例
1. 新用户首次 Google 登录：
   - `user_login_identity` 已创建
   - `user_profile` 创建成功，`id = user_id`
2. 新用户首次 Microsoft / GitHub 登录：同上
3. 老用户重复登录：
   - `user_profile` 不重复插入
4. provider 缺字段：
   - 对应 profile 字段为 `NULL`（或保持 DB 默认行为）
5. profile 初始化异常：
   - 登录仍成功，按原逻辑跳转

## 非目标
- 不在本次流程更新头像。
- 不在本次流程覆盖已存在 profile。
- 不新增独立资料编辑功能。
