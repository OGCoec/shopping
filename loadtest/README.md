# Coupon Claim Load Test

This directory contains the local JMeter assets for coupon claim concurrency checks.

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

## 7. Duplicate payment callback card secret flow

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
