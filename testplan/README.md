# outbox-eventual-consistency JMeter Test Plan

## Prerequisites

| Requirement | Detail |
|---|---|
| JMeter | >= 5.6, with PostgreSQL JDBC driver (`postgresql-*.jar`) in `$JMETER_HOME/lib/` |
| Jedis | `jedis-*.jar` in `$JMETER_HOME/lib/` (for Thread Group D Redis seeding) |
| Five PostgreSQL instances | CORE:5433 TRADE:5434 PRODUCT:5435 COUPON:5436 RISK:5437 |
| RabbitMQ | Running locally |
| Application | Started with env vars below |

## Required application env vars

```
ORDER_LOADTEST_BYPASS_GUARDS=true          # disables CSRF on /shopping/user/api/orders/**
APP_OUTBOX_FAULT_ENABLED=true             # enables FaultInjector
FAULT_PROB_ORDER_STOCK_DEDUCT=0.3         # 30% chance consumer throws (optional, for fault testing)
FAULT_PROB_ORDER_INVENTORY_RELEASE=0.0
FAULT_PROB_CARD_SECRET_IMPORT=0.0
FAULT_PROB_COUPON_EXPIRE=0.0
FAULT_PROB_ACCOUNT_STATUS_SYNC=0.3
FAULT_PROB_ACCOUNT_RISK_RECOVERY=0.0
```

## Before running

1. Get a valid JWT access token (log in via browser, copy `ACCESS_TOKEN` cookie value or `Authorization: Bearer` from DevTools).
2. Run `OrderAccessTokenSkuCsvExportMain` to export a SKU ID for ordering.
3. Open the JMX in JMeter GUI and update User Defined Variables:
   - `TOKEN` - the JWT from step 1
   - `SKU_ID` - a valid SKU id (hex string)
   - `FAULT_USER_ID` - a real userId for Thread Group D auth-risk-lock test
   - `DB_PASS` - your postgres password (default `123456`)

## Thread Groups

| Group | Endpoint | What it validates |
|---|---|---|
| A: order-create-saga | `POST /shopping/user/api/orders` | Initial `STOCK_CONFIRMING`, convergence to `PENDING_PAYMENT` or `CANCELLED`, source not rolled back |
| B: order-cancel | `POST /shopping/user/api/orders/{orderNo}/cancel` | TRADE status=`CANCELLED`, outbox event exists |
| C: idempotency-check | PRODUCT JDBC | `inbox_event` count=1 for order-stock-deduct |
| D: auth-risk-lock | Redis + RISK/CORE JDBC | Seeds Redis failure counters, asserts RISK lock written, CORE status=LOCKED eventually, CORE inbox idempotent |

## Fault injection (target consumer throws, source not rolled back)

Set `FAULT_PROB_ORDER_STOCK_DEDUCT=1.0` and run Thread Group A.
Expected:
- `trade_order` exists (source not rolled back)
- PRODUCT `inbox_event.status = FAILED`
- Message enters dead-letter queue

To trigger per-message precise fault: set `loadtestFault=THROW` in the order create request body:
```json
{"skuId":"...","quantity":1,"idempotencyKey":"...","loadtestFault":"THROW"}
```
Note: `loadtestFault` is carried in the outbox payload to the consumer.

## RabbitMQ stop/start test (manual)

1. `docker stop rabbitmq`
2. Run Thread Group A (a few orders)
3. Check TRADE `outbox_event` - status should be `NEW` or `RETRY`, `trade_order` exists
4. `docker start rabbitmq`
5. Wait ~30s, check TRADE `outbox_event` - status should be `PUBLISHED`, PRODUCT inventory deducted

## SqlRoute diagnostics

To verify each write hits the correct DB port, temporarily set in `application.yaml`:
```yaml
shopping:
  datasource:
    route-diagnostics:
      enabled: true
```
Then check app logs for `[SqlRoute]` entries confirming TRADE writes hit 5434 and PRODUCT writes hit 5435.
Remember to set back to `false` after verification.