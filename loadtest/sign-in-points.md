# Sign In Points JMeter Test

This test covers the second-level sign-in mode used during local testing:

```text
POST https://127.0.0.1:6655/shopping/user/api/sign-in
```

It verifies:

- user `2`: continuous 3, 7, 30, and 33 period rewards.
- user `3`: a skipped second resets `continuous_count` to `1`.
- user `1`: 50 concurrent clicks in the same second are idempotent.

## Inputs

Use the token CSV that currently matches the local database token versions:

```text
loadtest-output/xss-users-token.csv
```

The runner extracts three one-user CSV files into each run directory. It does not copy access tokens into tracked files.

## Run

Start the app with second-level sign-in periods:

```powershell
$env:SHOPPING_SIGN_IN_PERIOD_UNIT = 'SECOND'
$env:SIGN_IN_LOADTEST_BYPASS_GUARDS = 'true'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = '80'
```

The bypass flag only skips CSRF, preauth, WebRTC, account-network, and phone-binding guards for the sign-in API during local load testing. The access token interceptor still runs, so the request user still comes from the bearer token.
The larger Hikari pool is for the 50-thread same-user concurrency scenario, where many requests may wait behind the same user-level advisory lock.
The JMeter plan aligns both the continuous user and concurrent user to second boundaries, so the test is not dependent on when the run happens to start inside the current second.

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

Continuous user `2`:

```text
33 sign rows.
3rd row reward_points = 3.
7th row reward_points = 10.
30th row reward_points = 50.
33rd row reward_points = 3.
available_points = 95.
total_earned_points = 95.
```

Reset user `3`:

```text
2 sign rows.
Second row continuous_count = 1.
Second row cycle_day = 1.
available_points = 2.
total_earned_points = 2.
```

Concurrent user `1`:

```text
50 concurrent requests.
Exactly 1 sign row.
No duplicated sign_period_key.
Point account only increases by one successful reward.
```

The JMeter plan marks unexpected HTTP status, unexpected business code, wrong milestone rewards, and duplicate reward responses as failed samples. The database SQL is the final acceptance check.
