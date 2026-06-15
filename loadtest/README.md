# Coupon Claim Load Test

This directory contains the local JMeter assets for coupon claim concurrency checks.

## File index

| Type | Path | Purpose |
| --- | --- | --- |
| PowerShell runners | `loadtest/scripts/*.ps1` | Run JMeter plans, create per-run output directories, summarize JTL files, and optionally run PostgreSQL verification SQL. |
| JMeter plans | `loadtest/jmeter/*.jmx` | Cover coupon claim concurrency, hot SKU order creation, soft-close payment, batch payment callback refund, and duplicate callback card-secret delivery. |
| Verification SQL | `loadtest/sql/*.sql` | Verify database state after RabbitMQ/schedulers finish: claim uniqueness, stock safety, refund rows, callback inbox idempotency, and card-secret delivery. |
| Manual localhost requests | `loadtest/localhost/*.http` | Hand-run localhost smoke requests for targeted scenarios. |
| Java seed/export mains | `shopping-web/src/main/java/com/example/ShoppingSystem/tools/loadtest` | Seed users/coupons/SKUs/orders and export access-token or input CSV files used by JMeter. |
| Local run output | `loadtest-output/` | Stores JTL, logs, HTML reports, CSV inputs, summaries, and app run logs. This directory is ignored by Git. |

Current Java tools:

```text
CouponLoadtestUserSeedMain
CouponLoadtestCouponSeedMain
CouponAccessTokenCsvExportMain
OrderLoadtestHotSkuSeedMain
OrderAccessTokenSkuCsvExportMain
OrderDuplicateCallbackCardSecretSeedMain
```

Current JMeter plans:

```text
coupon-claim-same-user.jmx
coupon-claim-different-users.jmx
order-create-hot-sku.jmx
order-soft-close-payment-flow.jmx
order-callback-batch-refund-flow.jmx
order-duplicate-callback-card-secret-flow.jmx
```

## 1. Start the app for load test

Set the bypass flag before starting Spring Boot:

```powershell
$env:COUPON_LOADTEST_BYPASS_GUARDS = 'true'
```

The flag bypasses preAuth, WebRTC IP consistency, post-login network risk, phone binding, and CSRF for `/shopping/user/api/coupons/**`. The access token interceptor still runs and supplies `userId`.

## 2. Seed load test users and export access tokens

Compile first:

```powershell
mvn -q -pl shopping-web -am compile
```

Seed load test users `1..500` in one transaction:

```powershell
mvn -q -pl shopping-web exec:java `
  -Dexec.mainClass=com.example.ShoppingSystem.tools.loadtest.CouponLoadtestUserSeedMain `
  -Dexec.args="1 500"
```

The seeder creates one short-lived Hikari pool, borrows one connection, and executes two batch SQL statements:

```text
user_login_identity: id=user_id=1..500
user_profile: id=1..500
```

It refreshes `token_version` when re-run, so regenerate CSV after every seed run.

Export 500 access tokens for users `1..500` that expire on 2026-07-31:

```powershell
mvn -q -pl shopping-web exec:java `
  -Dexec.mainClass=com.example.ShoppingSystem.tools.loadtest.CouponAccessTokenCsvExportMain `
  -Dexec.args="500 loadtest-output/coupon-users-token.csv 2026-07-31T23:59:59-07:00 1 500"
```

This creates:

```text
loadtest-output/coupon-users-token.csv
loadtest-output/same-user-token.csv
```

The exporter creates one short-lived Hikari pool, borrows one connection, runs one batch SQL query, signs tokens in memory, then closes the pool.

Optional DB override environment variables:

```powershell
$env:SHOPPING_LOADTEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/shopping'
$env:SHOPPING_LOADTEST_DB_USERNAME = 'postgres'
$env:SHOPPING_LOADTEST_DB_PASSWORD = '123456'
```

## 3. Run JMeter

Same-user test:

```powershell
.\loadtest\scripts\run-coupon-claim.ps1 `
  -Mode same `
  -CouponTemplateId '<base62-coupon-template-id>' `
  -Threads 500 `
  -RampUp 1
```

Different-users stock safety test:

```powershell
.\loadtest\scripts\run-coupon-claim.ps1 `
  -Mode different `
  -CouponTemplateId '<base62-coupon-template-id>' `
  -Threads 500 `
  -RampUp 1
```

Each run writes:

```text
loadtest-output/runs/<timestamp>-<mode>/coupon-claim.jtl
loadtest-output/runs/<timestamp>-<mode>/jmeter.log
loadtest-output/runs/<timestamp>-<mode>/html-report/
loadtest-output/runs/<timestamp>-<mode>/summary.csv
```

## 4. Expected results

Same-user test:

```text
1 request succeeds with HTTP 200.
Remaining requests return HTTP 409 with businessCode=COUPON_ALREADY_CLAIMED.
```

Different-users test with stock 100 and 500 users:

```text
At most 100 requests succeed with HTTP 200.
Remaining requests return HTTP 409 with businessCode=COUPON_SOLD_OUT.
No 401, 403, 423, or 428 should appear while the bypass flag is enabled.
```

Use `loadtest/sql/verify-coupon-claim.sql` after RabbitMQ has finished consuming to verify DB rows and duplicate claims.

## 5. Order soft-close payment flow

Start the app with order bypass enabled so the test focuses on order/payment business logic while still using access tokens:

```powershell
$env:ORDER_LOADTEST_BYPASS_GUARDS = 'true'
$env:ORDER_EXPIRE_TTL_MILLIS = '300000'
$env:ORDER_EXPIRE_CLOSING_GRACE_MILLIS = '300000'
```

Run the full hot SKU soft-close payment flow with one SKU reset to stock 50:

```powershell
.\loadtest\scripts\run-order-soft-close-payment.ps1 `
  -SeedHotSku `
  -Stock 50 `
  -Threads 500 `
  -RampUp 1 `
  -TokenSkuCsv loadtest-output/order-create-single-hot-token-sku.csv `
  -Verify
```

The plan uses random order quantity `1..5`, writes successful order scenarios to `order-soft-close-success-orders.csv`, and runs for more than 10 minutes because it waits for the real 5-minute payment TTL and 5-minute soft-close grace period.

Expected business behavior:

```text
Successful order item quantity never exceeds 50.
PAY_NOW orders become PAID.
CLOSING_CALLBACK and BOUNDARY_DELAYED_CALLBACK orders become PAID after CLOSING.
CLOSING_USER_PAY_NEGATIVE orders reject /pay while CLOSING and finally become CLOSED.
CLOSED_CALLBACK_NEGATIVE orders stay CLOSED when callback arrives after final close, and the async callback path creates a refund record.
No unexpected 401, 403, 423, or 428 should appear while order bypass is enabled.
```

Card secret delivery checks are included in the verification SQL. Before running this flow, make sure the target SKU has enough pre-imported card secrets:

```sql
SELECT to_base62(sku_id) AS sku_id,
       status,
       COUNT(*) AS card_count
FROM card_secret_inventory
GROUP BY sku_id, status
ORDER BY sku_id, status;
```

Expected card secret behavior:

```text
PAID orders receive exactly quantity DELIVERED card secrets.
PENDING_PAYMENT, CLOSING, CLOSED, CANCELLED, and abnormal callback refund paths do not receive card secrets.
The same card_secret_id must never appear in more than one delivery record.
SOLD card_secret_inventory rows must match DELIVERED order_card_secret_delivery rows.
```

## 6. Payment callback batch refund flow

Use this plan when you already have a callback input CSV and want to stress the async callback inbox:

```text
orderNo,externalTradeNo,paidAmountYuan,paymentProvider,delayMs,expectedOutcome
2YdK...,CB-20260602-001,179.00,SIMULATED,0,PAID
2YdK...,CB-20260602-002,179.00,SIMULATED,0,REFUND_PENDING
```

Run:

```powershell
.\loadtest\scripts\run-order-callback-batch-refund.ps1 `
  -CallbackCsv loadtest-output/order-callback-batch-input.csv `
  -Threads 500 `
  -RampUp 1 `
  -Verify
```

The callback API should return `ORDER_PAYMENT_CALLBACK_RECEIVED` immediately. The 5-second scheduler then batch claims `payment_callback_inbox`, processes Redis in one Lua call, processes Redis-missing callbacks through one DB fallback SQL, batch inserts refund records for abnormal orders, and leaves refund execution to the existing refund dispatcher.

The callback verification SQL also checks that `REFUND_PENDING` callback outcomes do not deliver card secrets, and that `PAID` / `PAID_IDEMPOTENT` repeated callbacks do not over-deliver card secrets beyond the order item quantity.

## 7. Payment callback + refund Redis Stream flow

Use the Stream runner after the payment callback entry has been changed to Redis Stream buffering. It reuses the callback JMeter plan because the HTTP API did not change, but it verifies the new main chain:

```text
callback API -> callback Redis Stream -> payment_callback_inbox -> order state / refund record -> refund Redis Stream -> payment_refund_record
```

Run:

```powershell
.\loadtest\scripts\run-order-callback-refund-stream.ps1 `
  -CallbackCsv loadtest-output/order-callback-batch-input.csv `
  -Threads 500 `
  -RampUp 1 `
  -Verify `
  -RedisCheck
```

Expected behavior:

```text
Every callback request returns ORDER_PAYMENT_CALLBACK_RECEIVED.
Callback inbox rows are eventually PROCESSED by the callback Stream flusher.
PAID / PAID_IDEMPOTENT callbacks leave the order PAID and do not create refunds.
REFUND_PENDING callbacks create payment_refund_record rows.
SIMULATED refund providers eventually move refund rows to REFUNDED through the refund Stream flusher.
RedisCheck records callback/refund Stream XLEN and XPENDING snapshots in the run output directory.
```

## 8. Duplicate payment callback card secret flow

Use this plan when you want to prove that repeated third-party payment callbacks do not create duplicate paid-order handling or over-deliver card secrets.

The seed main creates pending orders directly in PostgreSQL and writes repeated callback rows:

```powershell
mvn -q -pl shopping-web exec:java `
  -Dexec.mainClass=com.example.ShoppingSystem.tools.loadtest.OrderDuplicateCallbackCardSecretSeedMain `
  -Dexec.args="2 2 5 loadtest-output/order-duplicate-callback-card-secret-input.csv duplicate-callback-local false"
```

By default it reuses an ACTIVE SKU that has enough `UNUSED` `card_secret_inventory` rows. It requires enough inventory for `orders * quantityPerOrder * duplicateCallbacksPerOrder`, so a real over-delivery bug is not hidden by shortage. Pass the script switch `-SeedIfShortage` only when the load test is allowed to insert a loadtest SKU and card secrets.

Run the full flow:

```powershell
.\loadtest\scripts\run-order-duplicate-callback-card-secret.ps1 `
  -Orders 2 `
  -QuantityPerOrder 2 `
  -DuplicateCallbacksPerOrder 5 `
  -Threads 10 `
  -RampUp 1 `
  -Verify
```

Expected behavior:

```text
Every callback request returns ORDER_PAYMENT_CALLBACK_RECEIVED.
Each order_no + external_trade_no has exactly one payment_callback_inbox row.
Each target order becomes PAID.
Each target order receives exactly quantity DELIVERED card secrets.
The same card_secret_id must never be delivered to more than one order.
The matching card_secret_inventory rows must be SOLD and attached to the target order.
```

For a manual localhost smoke test, generate one order with the seed main and then fill the placeholders in:

```text
loadtest/localhost/order-duplicate-callback-card-secret.http
```

## 9. Local result summary

The table below summarizes local `loadtest-output/runs/*/summary.csv` files found on this machine. These generated files are intentionally ignored by Git, so the table is a readable snapshot, not a committed source of truth.

| Scenario | Latest referenced run | Total | Success | Failure | Error rate | Avg ms | P95 ms | QPS | Notes |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Duplicate payment callback card secret | `20260604-013126-order-duplicate-callback-card-secret` | 200 | 200 | 0 | 0% | 287.44 | 710 | 72.75 | Latest local run has no JMeter sample failures. |
| Order soft-close payment | `20260603-095827-order-soft-close-payment` | 595 | 344 | 251 | 42.18% | 1719.46 | 4409 | 0.94 | Includes expected negative business paths; verification SQL is the business acceptance check. |
| Payment callback batch refund | `20260603-010559-order-callback-batch-refund` | 12 | 12 | 0 | 0% | 731.58 | 992 | 5.56 | Local callback/refund inbox run completed without JMeter sample failures. |
| Order hot SKU create | `20260601-113009-order-single-hot` | 500 | 500 | 0 | 0% | 2677.62 | 3854 | 86.69 | Local hot SKU order-create run completed without JMeter sample failures. |
| Coupon same-user claim | `20260531-014004-same` | 500 | 354 | 146 | 29.2% | 2488.71 | 3960 | 106.22 | Same-user scenario expects many duplicate-claim rejections; check `verify-coupon-claim.sql` for DB invariants. |
| Coupon different-users claim | `20260531-013808-different` | 500 | 462 | 38 | 7.6% | 1923.21 | 2552 | 146.76 | Stock safety scenario may include expected sold-out responses depending on coupon stock. |

For scenarios without a current local summary, use the matching runner and verification SQL listed above. Do not treat a missing `loadtest-output` run as a pass.
