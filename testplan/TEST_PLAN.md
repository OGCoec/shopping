# Outbox/Inbox 最终一致性 — 完整测试计划

## 测试目标

验证以下六条跨库链路的最终一致性：

| # | 链路 | 源库 (port) | 目标库 (port) |
|---|------|-------------|---------------|
| 1 | 订单创建 Saga | TRADE 5434 | PRODUCT 5435 |
| 2 | 订单取消 | TRADE 5434 | PRODUCT 5435 |
| 3 | 订单过期/关闭补偿 | TRADE 5434 | PRODUCT 5435 |
| 4 | 优惠券模板过期 | COUPON 5436 | TRADE 5434 |
| 5 | 卡密库存导入 | TRADE 5434 | PRODUCT 5435 |
| 6 | 登录风控锁号 | RISK 5437 | CORE 5433 |

**不验证原子性**：源库提交后、目标库消费前存在短暂不一致，这是设计预期。
验证的三个不变式：
- 源库本地事务（业务表 + outbox_event）原子提交，不因目标库失败而回滚
- 目标库消费失败时 inbox_event.status = FAILED，业务写回滚，消息重投后收敛
- inbox_event(event_id, consumer_name) 幂等，同一事件只处理一次

---

## 测试文件清单

```
testplan/
  outbox-eventual-consistency.jmx    JMeter 测试计划（四线程组 + 五库 JDBC 断言）
  seed-redis-auth-failure.ps1        灌 Redis 失败计数触发风控锁号
  verify-consistency.ps1             用 psql 对四库跑一致性断言（可放 CI）
  sql/
    assert-order-saga.sql            订单 Saga 专项断言（TRADE + PRODUCT）
    assert-auth-risk-lock.sql        登录风控专项断言（RISK + CORE）
  TEST_PLAN.md                       本文件
```

---

## Phase 0：环境准备

### 0.1 基础设施

确保以下服务全部运行：

| 服务 | 地址 |
|------|------|
| PostgreSQL CORE | 127.0.0.1:5433 db=shopping_core |
| PostgreSQL TRADE | 127.0.0.1:5434 db=shopping_trade |
| PostgreSQL PRODUCT | 127.0.0.1:5435 db=shopping_product |
| PostgreSQL COUPON | 127.0.0.1:5436 db=shopping_coupon |
| PostgreSQL RISK | 127.0.0.1:5437 db=shopping_risk |
| RabbitMQ | 127.0.0.1:5672 |
| Redis | 127.0.0.1:6380 |

验证命令：
```powershell
psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade -c "SELECT 1"
psql -h 127.0.0.1 -p 5435 -U postgres -d shopping_product -c "SELECT 1"
psql -h 127.0.0.1 -p 5437 -U postgres -d shopping_risk -c "SELECT 1"
psql -h 127.0.0.1 -p 5433 -U postgres -d shopping_core -c "SELECT 1"
redis-cli -p 6380 ping
```

### 0.2 启动应用（Phase 1 正常路径）

```powershell
$env:ORDER_LOADTEST_BYPASS_GUARDS     = "true"   # 关闭订单接口 CSRF，不免登录
$env:APP_OUTBOX_FAULT_ENABLED         = "false"  # Phase 1 先不注入故障
$env:FAULT_PROB_ORDER_STOCK_DEDUCT    = "0.0"
$env:FAULT_PROB_ORDER_INVENTORY_RELEASE = "0.0"
$env:FAULT_PROB_CARD_SECRET_IMPORT    = "0.0"
$env:FAULT_PROB_COUPON_EXPIRE         = "0.0"
$env:FAULT_PROB_ACCOUNT_STATUS_SYNC   = "0.0"
$env:FAULT_PROB_ACCOUNT_RISK_RECOVERY = "0.0"
```

### 0.3 获取测试数据

**JWT Token（必须）**
1. 浏览器访问 https://127.0.0.1:6655/shopping/user/login 登录
2. DevTools → Application → Cookies → 复制 ACCESS_TOKEN 的值
3. 或 DevTools → Network → 任意 API 请求 → Headers → Authorization: Bearer <token>
4. Token 有效期 3 小时，够跑完全套测试

**SKU_ID（订单测试必须）**
```powershell
# 运行项目自带的 seed 工具导出可用 SKU
mvn -pl shopping-web exec:java -Dexec.mainClass="com.example.ShoppingSystem.tools.loadtest.OrderAccessTokenSkuCsvExportMain"
# 输出 CSV 里取一个 skuId（hex 字符串，32位）
```

**USER_ID（风控测试必须）**
```sql
-- 在 CORE 库找一个测试用户 ID
psql -h 127.0.0.1 -p 5433 -U postgres -d shopping_core \
  -c "SELECT user_id, email, status FROM user_login_identity WHERE status='ACTIVE' LIMIT 5"
```

### 0.4 JMeter 前置

1. JMeter >= 5.6
2. 把 `postgresql-*.jar` 放到 `$JMETER_HOME/lib/`
3. 打开 `testplan/outbox-eventual-consistency.jmx`
4. 在 User Defined Variables 里填写：
   - `TOKEN` = 0.3 步骤获取的 JWT
   - `SKU_ID` = 0.3 步骤获取的 SKU hex
   - `FAULT_USER_ID` = 0.3 步骤获取的 user_id
   - `DB_PASS` = postgres 密码（默认 123456）

---

## Phase 1：正常路径验证

目标：确认每条链路在无故障情况下最终收敛。**Phase 1 全部通过才进 Phase 2。**

### TC-01 订单创建 Saga（TRADE → PRODUCT → TRADE）

**触发**：JMeter Thread Group A（50 线程，ramp-up 20s，循环 5 次）

**观察点 1**：HTTP 响应
- code = ORDER_CREATE_OK
- data.status = STOCK_CONFIRMING（初始中间态，证明订单已建但库存未确认）

**观察点 2**：TRADE DB 收敛（JMeter JSR223 轮询最多 8 秒）
- trade_order.status 变为 PENDING_PAYMENT（扣库存成功）或 CANCELLED（扣库存失败）
- 不得停在 STOCK_CONFIRMING

**观察点 3**：支付入口拒绝 STOCK_CONFIRMING
```bash
# 在 STOCK_CONFIRMING 期间尝试支付，应返回错误
curl -k -X POST https://127.0.0.1:6655/shopping/user/api/orders/{orderNo}/pay \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"externalTradeNo":"test-001"}'
# 预期：400/409 ORDER_PAY_UNAVAILABLE
```

**SQL 断言**（跑完后执行）：
```bash
psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -v order_no="'ORDER_NO_HERE'" \
  -f testplan/sql/assert-order-saga.sql
```

**通过标准**：
- [ ] order exists in TRADE: PASS
- [ ] order not stuck in STOCK_CONFIRMING: PASS
- [ ] outbox_event PUBLISHED: PASS
- [ ] PRODUCT inbox_event count=1 PROCESSED: PASS

---

### TC-02 订单取消（TRADE → PRODUCT）

**触发**：JMeter Thread Group B，对 TC-01 产生的 PENDING_PAYMENT 订单发取消

```
POST /shopping/user/api/orders/{orderNo}/cancel
```

**观察点**：
- HTTP 响应 code = ORDER_CANCEL_OK
- TRADE trade_order.status = CANCELLED
- TRADE outbox_event event_id = order-inventory-release:{orderNo}:CANCEL 状态为 PUBLISHED
- PRODUCT inbox_event event_id 同上，consumer_name = order-inventory-release-product，status = PROCESSED

**SQL 断言**：
```bash
psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -v order_no="'ORDER_NO_HERE'" \
  -f testplan/sql/assert-order-saga.sql
```

**通过标准**：
- [ ] TRADE status=CANCELLED: PASS
- [ ] TRADE cancel outbox event PUBLISHED: PASS
- [ ] PRODUCT inventory release inbox PROCESSED: PASS

---

### TC-03 幂等验证（重复消费同一事件）

**触发**：手工在 RabbitMQ 管理台（http://127.0.0.1:15672）重新投递已处理的消息

1. 进入 Queues → 找到 order.stock.deduct.product.queue
2. 取一条已 PUBLISHED 的消息，手动 Publish message（填入同样的 eventId）
3. 等 2 秒

**JMeter Thread Group C 断言**：
```sql
-- 在 PRODUCT 库执行
SELECT count(*) FROM inbox_event
WHERE event_id = 'order-stock-deduct-requested:{orderNo}'
  AND consumer_name = 'order-stock-deduct-product';
-- 预期：1，不能是 2
```

**通过标准**：
- [ ] inbox_event count = 1: PASS（重复投递被幂等拦截）

---

### TC-04 登录风控锁号（RISK → CORE）

**触发**：
```powershell
# Step 1：灌 Redis 失败计数到阈值（单类 8 次，总计 15 次）
.\testplan\seed-redis-auth-failure.ps1 -UserId YOUR_USER_ID -RedisPort 6380

# Step 2：让该用户触发一次登录尝试（密码随意，触发 triggerLock 路径）
curl -k -X POST https://127.0.0.1:6655/shopping/user/login/password \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"wrongpassword"}'
```

**等待 3-10 秒后运行 SQL 断言**：
```bash
# RISK DB
psql -h 127.0.0.1 -p 5437 -U postgres -d shopping_risk \
  -v user_id=YOUR_USER_ID \
  -f testplan/sql/assert-auth-risk-lock.sql

# CORE DB（同一文件，切换端口）
psql -h 127.0.0.1 -p 5433 -U postgres -d shopping_core \
  -v user_id=YOUR_USER_ID \
  -f testplan/sql/assert-auth-risk-lock.sql
```

**或一键跑全部断言**：
```powershell
.\testplan\verify-consistency.ps1 -UserId YOUR_USER_ID
```

**通过标准**：
- [ ] RISK user_risk_profile.lock_count > 0: PASS
- [ ] RISK outbox_event status=PUBLISHED: PASS
- [ ] CORE user_login_identity.status = LOCKED: PASS（最终一致，可能需要等待）
- [ ] CORE inbox_event count=1 PROCESSED: PASS

---

### TC-05 Phase 1 一键验证

跑完 TC-01 ~ TC-04 后，用 verify-consistency.ps1 做最终确认：

```powershell
.\testplan\verify-consistency.ps1 `
  -OrderNo "ORD-xxx" `
  -UserId YOUR_USER_ID `
  -PgPass "123456"
# 预期输出：=== Results: N passed, 0 failed ===
```

---

## Phase 2：故障注入验证

**目标**：验证目标库消费失败时，源库不回滚，消息重投后最终收敛。

重启应用，开启故障注入：

```powershell
$env:APP_OUTBOX_FAULT_ENABLED      = "true"
$env:FAULT_PROB_ORDER_STOCK_DEDUCT = "1.0"   # TC-06: 100% 失败
```

---

### TC-06 扣库存消费者抛异常（精确命中单条）

**触发**：发一个带 loadtestFault 标记的创建订单请求：

```bash
curl -k -X POST https://127.0.0.1:6655/shopping/user/api/orders \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "skuId": "YOUR_SKU_ID",
    "quantity": 1,
    "idempotencyKey": "fault-test-001",
    "loadtestFault": "THROW"
  }'
```

注意：`loadtestFault=THROW` 透传到 outbox payload，消费者收到后 FaultInjector 抛 `IllegalStateException("INJECTED_FAULT:order-stock-deduct-product")`。

**等待 2 秒后验证三个不变式**：

不变式 1 — 源库不回滚：
```bash
psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -c "SELECT order_no, status FROM trade_order WHERE order_no='ORDER_NO_FROM_RESPONSE'"
# 预期：存在，status=STOCK_CONFIRMING（消费失败，尚未转态）
```

不变式 2 — 目标库消费回滚，inbox 标 FAILED：
```bash
psql -h 127.0.0.1 -p 5435 -U postgres -d shopping_product \
  -c "SELECT event_id, status FROM inbox_event WHERE event_id='order-stock-deduct-requested:ORDER_NO'"
# 预期：status=FAILED（不是 PROCESSED）
```

不变式 3 — 消息进死信：
```
RabbitMQ 管理台 http://127.0.0.1:15672
Queues → order.stock.deduct.product.dlq → 有消息
```

**通过标准**：
- [ ] TRADE trade_order exists: PASS（源库没被错误回滚）
- [ ] PRODUCT inbox_event status=FAILED: PASS（目标库事务回滚）
- [ ] RabbitMQ DLQ 有消息: PASS

---

### TC-07 重投后最终收敛

**触发**：把故障概率调回 0，等 outbox dispatcher 重投（最多 60 秒）：

```powershell
$env:FAULT_PROB_ORDER_STOCK_DEDUCT = "0.0"
# 重启应用或等 dispatcher 下一轮扫描
```

**验证**：
```bash
psql -h 127.0.0.1 -p 5435 -U postgres -d shopping_product \
  -c "SELECT event_id, status FROM inbox_event WHERE event_id='order-stock-deduct-requested:ORDER_NO'"
# 预期：status 变为 PROCESSED

psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -c "SELECT order_no, status FROM trade_order WHERE order_no='ORDER_NO'"
# 预期：status=PENDING_PAYMENT 或 CANCELLED（不再 STOCK_CONFIRMING）
```

**通过标准**：
- [ ] PRODUCT inbox_event status=PROCESSED: PASS（最终收敛）
- [ ] TRADE trade_order status != STOCK_CONFIRMING: PASS

---

### TC-08 账号状态同步消费者抛异常（RISK → CORE）

**重启应用**：
```powershell
$env:FAULT_PROB_ACCOUNT_STATUS_SYNC = "1.0"
```

**触发**：
```powershell
.\testplan\seed-redis-auth-failure.ps1 -UserId YOUR_USER_ID -RedisPort 6380
# 再触发一次登录
```

**验证三个不变式**：

```bash
# 不变式 1：RISK 源库写入正常（lock_count 已增加）
psql -h 127.0.0.1 -p 5437 -U postgres -d shopping_risk \
  -c "SELECT lock_count FROM user_risk_profile WHERE user_id=YOUR_USER_ID"
# 预期：lock_count > 0

# 不变式 2：CORE inbox FAILED（消费回滚，status 未被改）
psql -h 127.0.0.1 -p 5433 -U postgres -d shopping_core \
  -c "SELECT status FROM inbox_event WHERE consumer_name='account-status-sync-core' AND event_id LIKE 'acct-status-YOUR_USER_ID-%'"
# 预期：status=FAILED

# 不变式 3：CORE user_login_identity 未被错误修改
psql -h 127.0.0.1 -p 5433 -U postgres -d shopping_core \
  -c "SELECT status FROM user_login_identity WHERE user_id=YOUR_USER_ID"
# 预期：status 仍为 ACTIVE（消费失败，目标库业务写已回滚）
```

**关掉故障后收敛**：
```powershell
$env:FAULT_PROB_ACCOUNT_STATUS_SYNC = "0.0"
# 等 30-60 秒
```

验证：CORE `user_login_identity.status = LOCKED`，inbox `status = PROCESSED`。

**通过标准**：
- [ ] RISK lock_count > 0: PASS
- [ ] CORE inbox status=FAILED（注入期间）: PASS
- [ ] CORE user status 未被错误改: PASS
- [ ] CORE 最终 LOCKED（收敛后）: PASS

---

## Phase 3：MQ 停机重投

### TC-09 RabbitMQ 停机后源库数据不丢

```bash
docker stop rabbitmq
```

用 JMeter Thread Group A 打 5 单（或 curl 手动创建几单）。

**立即验证**（MQ 停机中）：
```bash
# TRADE 有单（源库本地事务已原子提交）
psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -c "SELECT order_no, status, created_at FROM trade_order ORDER BY created_at DESC LIMIT 5"
# 预期：status=STOCK_CONFIRMING，单已存在

# TRADE outbox_event 等待投递
psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -c "SELECT event_id, status, retry_count FROM outbox_event WHERE status IN ('NEW','RETRY') ORDER BY created_at DESC LIMIT 5"
# 预期：status=NEW，retry_count=0
```

**恢复 MQ**：
```bash
docker start rabbitmq
# 等 30 秒
```

**验证最终投递**：
```bash
psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -c "SELECT event_id, status, published_at FROM outbox_event WHERE status='PUBLISHED' ORDER BY published_at DESC LIMIT 5"
# 预期：status=PUBLISHED，published_at 有值

psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade \
  -c "SELECT order_no, status FROM trade_order WHERE status != 'STOCK_CONFIRMING' ORDER BY created_at DESC LIMIT 5"
# 预期：已收敛到 PENDING_PAYMENT 或 CANCELLED
```

**通过标准**：
- [ ] MQ 停机期间源库有单、outbox 状态 NEW: PASS
- [ ] MQ 恢复后 outbox 转 PUBLISHED，订单收敛: PASS

---

## Phase 4：端口路由诊断

### TC-10 验证每次写落正确端口

临时修改 `application.yaml`：
```yaml
shopping:
  datasource:
    route-diagnostics:
      enabled: true
```

重启应用，打一单创建再取消，查应用日志：

```powershell
# 过滤 SqlRoute 日志
Get-Content app.log | Select-String "\[SqlRoute\]"
```

**预期日志形式**：
```
[SqlRoute] routeContext=TRADE, expectedRoute=TRADE, jdbcPort=5434, ...
[SqlRoute] routeContext=PRODUCT, expectedRoute=PRODUCT, jdbcPort=5435, ...
[SqlRoute] routeContext=CORE, expectedRoute=CORE, jdbcPort=5433, ...
[SqlRoute] routeContext=RISK, expectedRoute=RISK, jdbcPort=5437, ...
```

验证完成后把 `enabled` 改回 `false`。

**通过标准**：
- [ ] 每次 TRADE 写落 5434: PASS
- [ ] 每次 PRODUCT 写落 5435: PASS
- [ ] 每次 CORE 写落 5433: PASS
- [ ] 每次 RISK 写落 5437: PASS
- [ ] 无 routeContext 与 jdbcPort 不匹配的日志: PASS

---

## 测试通过标准汇总

| TC | 名称 | 通过条件 |
|----|------|---------|
| TC-01 | 订单创建 Saga 正常路径 | 源库有单，不停 STOCK_CONFIRMING，PRODUCT inbox PROCESSED |
| TC-02 | 订单取消正常路径 | CANCELLED，库存释放 outbox PUBLISHED，inbox PROCESSED |
| TC-03 | 幂等验证 | 重复投递后 inbox count 仍为 1 |
| TC-04 | 登录风控锁号正常路径 | RISK 写入，CORE 最终 LOCKED，inbox 幂等 |
| TC-05 | Phase 1 一键断言 | verify-consistency.ps1 输出 0 failed |
| TC-06 | 故障注入：扣库存失败 | 源库不回滚，PRODUCT inbox FAILED，消息进 DLQ |
| TC-07 | 故障恢复：最终收敛 | inbox 变 PROCESSED，订单状态收敛 |
| TC-08 | 故障注入：账号同步失败 | RISK 正常，CORE 未被错改，收敛后 LOCKED |
| TC-09 | MQ 停机重投 | 停机期间源库不丢，MQ 恢复后 outbox PUBLISHED |
| TC-10 | 端口路由诊断 | 所有写落正确端口，无路由错误 |

---

## 已知限制

- 卡密库存导入（TRADE→PRODUCT）和优惠券模板过期（COUPON→TRADE）是管理员接口和定时任务，没有直接面向用户的 HTTP 入口，需要手工触发管理员接口或等待定时任务，SQL 断言方式与订单链路相同。
- JMeter Thread Group D 里的 Redis seeding 需要 Jedis jar；可替代方案是直接使用 seed-redis-auth-failure.ps1。
- MQ 停机测试需要本地有 Docker，或直接在 RabbitMQ 管理台手工 stop/start broker。