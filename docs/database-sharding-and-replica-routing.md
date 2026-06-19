# 数据库纵向分库与读从库路由

本文说明当前本地开发环境的 PostgreSQL 纵向分库、物理从库和 Spring 应用层读写路由。项目现在不再按“单个 `5432/shopping` 库”理解数据库访问。

## 总体原则

- `5432/shopping` 保留为旧库、迁移来源和回滚来源，新链路默认不再访问它。
- 新业务库按业务域纵向拆分到 `5433` 到 `5437`。
- 每个新主库可以有两个 PostgreSQL 物理从库，复制方式是 WAL streaming physical replication，不是逻辑复制。
- Spring 不使用 MyCat、ShardingJDBC 或 Nginx 做数据库路由，当前路由在应用内通过 `AbstractRoutingDataSource`、`ThreadLocal` 和领域 executor 完成。
- 写操作和强一致读走主库；普通列表、详情、后台查询等读操作按领域走对应从库。

## 主库端口与表归属

| 端口 / 数据库 | 业务域 | 表 |
| --- | --- | --- |
| `5432/shopping` | 旧库 | 保留不动，作为迁移来源和回滚库。 |
| `5433/shopping_core` | 用户基础 | `user_login_identity`、`user_profile`、`user_account_self_deletion` |
| `5434/shopping_trade` | 交易链路 | `trade_order`、`trade_order_item`、`payment_callback_inbox`、`payment_refund_record`、`order_card_secret_delivery`、`card_secret_inventory`、`user_point_account`、`user_sign_record`、`user_coupon`、`coupon_usage_record` |
| `5435/shopping_product` | 商品 | `product_category`、`product_spu`、`product_sku`、`product_detail`、`product_hot_sku` |
| `5436/shopping_coupon` | 静态券模板 | `coupon_template`、`coupon_scope` |
| `5437/shopping_risk` | 风控 | `ipv4_reputation_profile`、`ipv6_reputation_profile`、`user_risk_profile`、`device_risk_profile`、`device_user_relation`、`user_risk_score_event`、`device_risk_score_event`、`user_login_success_record`、`user_login_fail_record`、`user_risk_account_termination` |

`user_coupon` 和 `coupon_usage_record` 放在 `shopping_trade`，因为下单、锁券、用券和支付链路需要强一致。`coupon_template` 和 `coupon_scope` 放在 `shopping_coupon`，属于变化较少的静态券配置。

## 物理从库拓扑

| 业务域 | 主库 | 从库 1 | 从库 2 | 当前 Spring 读库状态 |
| --- | --- | --- | --- | --- |
| CORE | `5433/shopping_core` | `5533/shopping_core` | `5633/shopping_core` | 暂不接登录/认证读库。 |
| TRADE | `5434/shopping_trade` | `5534/shopping_trade` | `5634/shopping_trade` | 普通订单、回调、退款查询可读从库。 |
| PRODUCT | `5435/shopping_product` | `5535/shopping_product` | `5635/shopping_product` | 商品公开查询和后台商品查询可读从库。 |
| COUPON | `5436/shopping_coupon` | `5536/shopping_coupon` | `5636/shopping_coupon` | 优惠券模板、范围等普通查询可读从库。 |
| RISK | `5437/shopping_risk` | `5537/shopping_risk` | `5637/shopping_risk` | 风控后台普通查询可读从库。 |

从库根目录：

```text
E:\postgresql_replica_instances
```

主库根目录：

```text
E:\postgresql_instances
```

## Spring 路由实现

核心代码位于：

- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/DataSourceRoute.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/RoutingDataSource.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/RoutingDataSourceContext.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/RoutingDataSourceConfig.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/ShardDataSourceExecutor.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/ReadReplicaQueryRunner.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/ReadReplicaLoadBalancer.java`
- `shopping-web/src/main/java/com/example/ShoppingSystem/config/datasource/ClientIpResolver.java`

主库 route：

```text
CORE    -> 5433/shopping_core
TRADE   -> 5434/shopping_trade
PRODUCT -> 5435/shopping_product
COUPON  -> 5436/shopping_coupon
RISK    -> 5437/shopping_risk
```

读库 route：

```text
ORDER_READ_1   -> 5534/shopping_trade
ORDER_READ_2   -> 5634/shopping_trade
PRODUCT_READ_1 -> 5535/shopping_product
PRODUCT_READ_2 -> 5635/shopping_product
COUPON_READ_1  -> 5536/shopping_coupon
COUPON_READ_2  -> 5636/shopping_coupon
RISK_READ_1    -> 5537/shopping_risk
RISK_READ_2    -> 5637/shopping_risk
```

读库 executor：

```text
OrderReadReplicaQueryExecutor
ProductReadReplicaQueryExecutor
CouponReadReplicaQueryExecutor
RiskReadReplicaQueryExecutor
```

这些 executor 只适合包普通 `SELECT` 查询。不要把写操作、扣库存、扣积分、发卡密、Redis 状态修改等有副作用的逻辑放进 read executor，因为从库失败重试时会再次执行同一个 `Supplier`。

## IP Hash 负载均衡

读库选择逻辑在 `ReadReplicaLoadBalancer`：

```text
有 HTTP client IP：
  Math.floorMod(clientIp.hashCode(), 2)

没有 HTTP 请求上下文：
  AtomicInteger round-robin
```

客户端 IP 解析顺序：

```text
X-Forwarded-For 第一个非空且不是 unknown 的值
X-Real-IP
request.getRemoteAddr()
```

因此，同一个客户端 IP 会稳定落到同一个领域的同一个从库；非 Web 请求或没有请求上下文时，会在两个从库之间轮询。

## 从库失败与降级

每个读 executor 的普通查询遵循同一套降级规则：

```text
read disabled：
  直接走同领域主库

read enabled：
  按 IP hash 或 round-robin 选择第一个从库
  第一个从库失败后，自动重试另一个从库
  两个从库都失败后：
    fallback-to-primary=true  -> 回同领域主库
    fallback-to-primary=false -> 抛出最后一次异常
```

日志会记录：

```text
domain
selectedReplica
fallbackReplica
fallbackToPrimary
reason
```

降级只在同一个业务域内发生，例如订单读从库失败只能回 `TRADE` 主库，不会回 `CORE` 或旧 `5432/shopping`。

## 强一致读规则

不是所有 `SELECT` 都应该走从库。以下查询必须走主库：

- 刚创建订单后立即查订单详情。
- 支付成功后立即查订单。
- 扣积分后立即查余额。
- 发卡密后立即查发货结果。
- 用户查看订单卡密。
- 登录、认证、账号状态、token、账号封禁状态相关查询。

订单详情和卡密查询当前已经显式走 `OrderReadReplicaQueryExecutor.queryPrimary(...)`。登录和认证链路第一版仍走 `CORE` 主库，不接 `5533/5633`。

## 本地配置

主库配置位于 `shopping-web/src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: ${SHOPPING_CORE_DB_URL:jdbc:postgresql://127.0.0.1:5433/shopping_core}

shopping:
  datasource:
    trade:
      url: ${SHOPPING_TRADE_DB_URL:jdbc:postgresql://127.0.0.1:5434/shopping_trade}
    product:
      url: ${SHOPPING_PRODUCT_DB_URL:jdbc:postgresql://127.0.0.1:5435/shopping_product}
    coupon:
      url: ${SHOPPING_COUPON_DB_URL:jdbc:postgresql://127.0.0.1:5436/shopping_coupon}
    risk:
      url: ${SHOPPING_RISK_DB_URL:jdbc:postgresql://127.0.0.1:5437/shopping_risk}
```

读库配置示例：

```yaml
shopping:
  datasource:
    order-read:
      enabled: ${SHOPPING_ORDER_READ_DB_ENABLED:true}
      fallback-to-primary: ${SHOPPING_ORDER_READ_DB_FALLBACK_TO_PRIMARY:true}
      replicas:
        - url: ${SHOPPING_ORDER_READ_DB_REPLICA_1_URL:jdbc:postgresql://127.0.0.1:5534/shopping_trade?ApplicationName=shopping-order-read-1}
        - url: ${SHOPPING_ORDER_READ_DB_REPLICA_2_URL:jdbc:postgresql://127.0.0.1:5634/shopping_trade?ApplicationName=shopping-order-read-2}
```

本地默认密码可以保留 `123456` 作为开发兜底。生产环境不要使用默认密码，也不要把真实密码写进 Git；应改为环境变量、Secret 或部署平台的密钥管理。

## 常用环境变量

主库：

```text
SHOPPING_CORE_DB_URL
SHOPPING_CORE_DB_USERNAME
SHOPPING_CORE_DB_PASSWORD

SHOPPING_TRADE_DB_URL
SHOPPING_TRADE_DB_USERNAME
SHOPPING_TRADE_DB_PASSWORD

SHOPPING_PRODUCT_DB_URL
SHOPPING_PRODUCT_DB_USERNAME
SHOPPING_PRODUCT_DB_PASSWORD

SHOPPING_COUPON_DB_URL
SHOPPING_COUPON_DB_USERNAME
SHOPPING_COUPON_DB_PASSWORD

SHOPPING_RISK_DB_URL
SHOPPING_RISK_DB_USERNAME
SHOPPING_RISK_DB_PASSWORD
```

读库：

```text
SHOPPING_ORDER_READ_DB_ENABLED
SHOPPING_ORDER_READ_DB_FALLBACK_TO_PRIMARY
SHOPPING_ORDER_READ_DB_REPLICA_1_URL
SHOPPING_ORDER_READ_DB_REPLICA_2_URL
SHOPPING_ORDER_READ_DB_USERNAME
SHOPPING_ORDER_READ_DB_PASSWORD

SHOPPING_PRODUCT_READ_DB_ENABLED
SHOPPING_PRODUCT_READ_DB_FALLBACK_TO_PRIMARY
SHOPPING_PRODUCT_READ_DB_REPLICA_1_URL
SHOPPING_PRODUCT_READ_DB_REPLICA_2_URL
SHOPPING_PRODUCT_READ_DB_USERNAME
SHOPPING_PRODUCT_READ_DB_PASSWORD

SHOPPING_COUPON_READ_DB_ENABLED
SHOPPING_COUPON_READ_DB_FALLBACK_TO_PRIMARY
SHOPPING_COUPON_READ_DB_REPLICA_1_URL
SHOPPING_COUPON_READ_DB_REPLICA_2_URL
SHOPPING_COUPON_READ_DB_USERNAME
SHOPPING_COUPON_READ_DB_PASSWORD

SHOPPING_RISK_READ_DB_ENABLED
SHOPPING_RISK_READ_DB_FALLBACK_TO_PRIMARY
SHOPPING_RISK_READ_DB_REPLICA_1_URL
SHOPPING_RISK_READ_DB_REPLICA_2_URL
SHOPPING_RISK_READ_DB_USERNAME
SHOPPING_RISK_READ_DB_PASSWORD
```

## 本地脚本

从库启动：

```bat
E:\postgresql_replica_instances\start_shopping_replicas.bat
```

从库停止：

```bat
E:\postgresql_replica_instances\stop_shopping_replicas.bat
```

从库检查：

```bat
E:\postgresql_replica_instances\check_shopping_replicas.bat
```

端口检查：

```powershell
Get-NetTCPConnection -LocalPort 5433,5434,5435,5436,5437,5533,5534,5535,5536,5537,5633,5634,5635,5636,5637 -State Listen
```

预期：

```text
5433-5437 主库监听
5533-5537 第一组从库监听
5633-5637 第二组从库监听
```

`check_shopping_replicas.bat` 预期每个从库：

```text
pg_is_in_recovery = true
transaction_read_only = on
```

并且每个主库 `pg_stat_replication` 有 2 条 streaming 连接。

## IP2Location BIN 文件

本地 IP2Location BIN 文件很大，不提交 GitHub：

```text
IP2LOCATION-LITE-DB11.IPV6.BIN
```

仓库 `.gitignore` 已忽略该文件。开发环境需要自行从 IP2Location 官方下载，或通过 `REGISTER_IP_COUNTRY_CACHE_BIN_PATH` 指向本机实际路径。

## 维护注意事项

- 新增表时必须先确定业务域，再决定属于 `CORE`、`TRADE`、`PRODUCT`、`COUPON` 还是 `RISK`。
- 新增普通读接口时，应优先使用对应领域 read executor。
- 新增写接口或写后立即读接口时，必须走主库。
- 跨库 JOIN 不要直接写在 Mapper 里；应拆成两段查询后在 Java 组装，或者明确把依赖表放在同一个业务库。
- 复杂条件查询仍然需要索引、分页限制和 SQL 优化；读从库只解决读流量分摊，不解决慢 SQL 本身。
- 生产环境建议每个库独立账号和强密码，从库账号只给只读权限，应用写账号只给必要写权限。
