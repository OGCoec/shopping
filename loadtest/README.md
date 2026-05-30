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
