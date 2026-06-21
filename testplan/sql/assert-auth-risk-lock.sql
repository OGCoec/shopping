-- assert-auth-risk-lock.sql
-- Verifies RISK->CORE eventual consistency for auth failure lock flow.
-- Replace :user_id with the actual user id before running.
-- Usage:
--   RISK DB: psql -h 127.0.0.1 -p 5437 -U postgres -d shopping_risk -v user_id=12345 -f assert-auth-risk-lock.sql
--   CORE DB: psql -h 127.0.0.1 -p 5433 -U postgres -d shopping_core -v user_id=12345 -f assert-auth-risk-lock.sql

-- ============================================================
-- 1. RISK DB assertions (run on shopping_risk port 5437)
-- ============================================================

\echo '--- RISK: user_risk_profile lock state written ---'
SELECT
    user_id,
    lock_count,
    risk_level,
    CASE WHEN lock_count > 0 THEN 'PASS' ELSE 'FAIL: lock_count=0, AuthLockRiskWriter may not have fired' END AS check_status
FROM user_risk_profile
WHERE user_id = :user_id;

\echo ''
\echo '--- RISK: score event recorded ---'
SELECT
    event_type,
    score_before,
    score_after,
    lock_reason,
    created_at
FROM user_risk_score_event
WHERE user_id = :user_id
ORDER BY created_at DESC
LIMIT 3;

\echo ''
\echo '--- RISK: outbox_event for account-status-sync (should be PUBLISHED) ---'
SELECT
    event_id,
    event_type,
    status,
    retry_count,
    CASE WHEN status = 'PUBLISHED' THEN 'PASS' ELSE 'FAIL: status=' || status END AS check_status
FROM outbox_event
WHERE event_id LIKE 'acct-status-' || :user_id || '-%'
ORDER BY created_at DESC
LIMIT 3;

\echo ''
\echo '--- RISK: no FAILED outbox events for this user ---'
SELECT count(*) AS failed_count,
    CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'WARN: FAILED outbox events found' END AS check_status
FROM outbox_event
WHERE event_id LIKE 'acct-status-' || :user_id || '-%' AND status = 'FAILED';

-- ============================================================
-- 2. CORE DB assertions (run on shopping_core port 5433)
-- ============================================================

\echo ''
\echo '--- CORE: user_login_identity status (eventual: should be LOCKED) ---'
SELECT
    user_id,
    status,
    CASE WHEN status = 'LOCKED' THEN 'PASS' ELSE 'FAIL: status=' || status || ' (may still be propagating)' END AS check_status
FROM user_login_identity
WHERE user_id = :user_id;

\echo ''
\echo '--- CORE: inbox_event for account-status-sync (idempotency: must be exactly 1 PROCESSED) ---'
SELECT
    event_id,
    consumer_name,
    status,
    processed_at,
    CASE WHEN status = 'PROCESSED' THEN 'PASS' ELSE 'FAIL: status=' || status END AS check_status
FROM inbox_event
WHERE consumer_name = 'account-status-sync-core'
  AND event_id LIKE 'acct-status-' || :user_id || '-%'
ORDER BY created_at DESC
LIMIT 3;

\echo ''
\echo '--- CORE: no duplicate processing (count must be 1) ---'
SELECT count(*) AS inbox_count,
    CASE WHEN count(*) = 1 THEN 'PASS' ELSE 'FAIL: duplicate inbox entries count=' || count(*) END AS check_status
FROM inbox_event
WHERE consumer_name = 'account-status-sync-core'
  AND event_id LIKE 'acct-status-' || :user_id || '-%'
  AND status = 'PROCESSED';

\echo ''
\echo '--- CORE: inbox_event for risk-recovery (if recovery was triggered) ---'
SELECT
    event_id,
    consumer_name,
    status,
    CASE WHEN status IN ('PROCESSED','NEW') THEN 'PASS' ELSE 'FAIL: status=' || status END AS check_status
FROM inbox_event
WHERE consumer_name = 'account-risk-recovery-started-risk'
  AND event_id LIKE 'auth-lock-recovery-started:' || :user_id || ':%'
ORDER BY created_at DESC
LIMIT 3;