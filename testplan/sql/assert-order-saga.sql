-- assert-order-saga.sql
-- Run against TRADE (port 5434) and PRODUCT (port 5435) after an order create/cancel test.
-- Replace :order_no with the actual order number before running.
-- Usage (psql): psql -h 127.0.0.1 -p 5434 -U postgres -d shopping_trade -v order_no="'ORD-xxx'" -f assert-order-saga.sql

-- ============================================================
-- 1. TRADE DB assertions (run on shopping_trade port 5434)
-- ============================================================

\echo '--- TRADE: order exists (source not rolled back) ---'
SELECT
    order_no,
    status,
    CASE WHEN status != 'STOCK_CONFIRMING' THEN 'PASS' ELSE 'FAIL: stuck in STOCK_CONFIRMING' END AS check_status
FROM trade_order
WHERE order_no = :order_no;

\echo ''
\echo '--- TRADE: outbox_event for stock deduct (should be PUBLISHED) ---'
SELECT
    event_id,
    event_type,
    status,
    retry_count,
    CASE WHEN status = 'PUBLISHED' THEN 'PASS' ELSE 'FAIL: not PUBLISHED status=' || status END AS check_status
FROM outbox_event
WHERE event_id = 'order-stock-deduct-requested:' || :order_no;

\echo ''
\echo '--- TRADE: outbox_event for inventory release on CANCEL (if cancelled) ---'
SELECT
    event_id,
    status,
    retry_count,
    CASE WHEN status IN ('PUBLISHED','NEW','RETRY') THEN 'PASS' ELSE 'FAIL: status=' || status END AS check_status
FROM outbox_event
WHERE event_id = 'order-inventory-release:' || :order_no || ':CANCEL';

\echo ''
\echo '--- TRADE: no FAILED outbox events for this order ---'
SELECT count(*) AS failed_outbox_count,
    CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'WARN: some outbox events FAILED' END AS check_status
FROM outbox_event
WHERE event_id LIKE '%' || :order_no || '%' AND status = 'FAILED';

-- ============================================================
-- 2. PRODUCT DB assertions (run on shopping_product port 5435)
-- ============================================================

\echo ''
\echo '--- PRODUCT: inbox_event for stock deduct (idempotency: must be exactly 1 PROCESSED) ---'
SELECT
    event_id,
    consumer_name,
    status,
    CASE WHEN status = 'PROCESSED' THEN 'PASS' ELSE 'FAIL: status=' || status END AS check_status
FROM inbox_event
WHERE event_id = 'order-stock-deduct-requested:' || :order_no
  AND consumer_name = 'order-stock-deduct-product';

\echo ''
\echo '--- PRODUCT: no duplicate inbox_event for stock deduct ---'
SELECT count(*) AS inbox_count,
    CASE WHEN count(*) = 1 THEN 'PASS' ELSE 'FAIL: duplicate inbox entries count=' || count(*) END AS check_status
FROM inbox_event
WHERE event_id = 'order-stock-deduct-requested:' || :order_no
  AND consumer_name = 'order-stock-deduct-product';

\echo ''
\echo '--- PRODUCT: inbox_event for inventory release (if cancel triggered) ---'
SELECT
    event_id,
    consumer_name,
    status,
    CASE WHEN status IN ('PROCESSED','NEW') THEN 'PASS' ELSE 'FAIL: status=' || status END AS check_status
FROM inbox_event
WHERE event_id = 'order-inventory-release:' || :order_no || ':CANCEL'
  AND consumer_name = 'order-inventory-release-product';