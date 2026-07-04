# Sign In Points JMeter Test

This test covers the day-level sign-in mode:

```text
POST https://127.0.0.1:6655/shopping/user/api/sign-in
```

It verifies:

- user `2`: repeated same-day clicks are idempotent and only grant points once.
- user `3`: a different user can sign in on the same day.
- user `1`: 50 concurrent same-day clicks are idempotent.

The old second-level continuous sign-in scenario is deprecated after switching sign-in uniqueness to `user_id + sign_date`.

## Inputs

Use the token CSV that currently matches the local database token versions:

```text
loadtest-output/xss-users-token.csv
```

The runner extracts three one-user CSV files into each run directory. It does not copy access tokens into tracked files.

## Run

Start the app with day-level sign-in periods. The default app config is already day-level; only the local bypass and pool size are needed for this load test:

```powershell
$env:SIGN_IN_LOADTEST_BYPASS_GUARDS = 'true'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = '80'
```

The bypass flag only skips CSRF, preauth, WebRTC, account-network, and phone-binding guards for the sign-in API during local load testing. The access token interceptor still runs, so the request user still comes from the bearer token.
The larger Hikari pool is for the 50-thread same-user concurrency scenario, where many requests may wait behind the same user-level advisory lock.

Run the full JMeter flow:

```powershell
.\loadtest\scripts\run-sign-in-points.ps1 `
  -Clean `
  -Verify
```

The `-Clean` switch deletes rows for users `1`, `2`, and `3` from:

```text
user_sign_record
user_point_account
```

The `-Verify` switch runs:

```text
loadtest/sql/verify-sign-in-points.sql
```

The runner defaults to `postgresql://postgres@127.0.0.1:5434/shopping_trade` and does not include a password. Use `.pgpass`, `PGPASSWORD`, or pass `-PostgresUrl` with your local connection configuration.

If `psql` is not on `PATH`, pass it explicitly:

```powershell
.\loadtest\scripts\run-sign-in-points.ps1 `
  -Clean `
  -Verify `
  -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe"
```

If JMeter is not on `PATH`, the runner uses:

```text
E:\apache-jmeter-5.6.3\bin\jmeter.bat
```

or pass:

```powershell
-JMeterPath "E:\apache-jmeter-5.6.3\bin\jmeter.bat"
```

## Output

Each run writes:

```text
loadtest-output/runs/<timestamp>-sign-in-points/sign-in-points.jtl
loadtest-output/runs/<timestamp>-sign-in-points/jmeter.log
loadtest-output/runs/<timestamp>-sign-in-points/html-report/
loadtest-output/runs/<timestamp>-sign-in-points/summary.csv
loadtest-output/runs/<timestamp>-sign-in-points/verify-command.txt
```

## Expected Results

Repeat user `2`:

```text
2 same-day requests.
Exactly 1 sign row.
First request grants 1 point.
Duplicate request returns already signed and grants 0 points.
available_points = 1.
total_earned_points = 1.
```

Different same-day user `3`:

```text
Exactly 1 sign row.
Sign date can match other users.
available_points = 1.
total_earned_points = 1.
```

Concurrent user `1`:

```text
50 concurrent requests.
Exactly 1 sign row.
No duplicated user_id + sign_date.
Point account only increases by one successful reward.
```

The JMeter plan marks unexpected HTTP status, unexpected business code, and duplicate reward responses as failed samples. The database SQL is the final acceptance check.
