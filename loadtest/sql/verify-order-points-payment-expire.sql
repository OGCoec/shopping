-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" `
--     -v run_id=<run-id> `
--     -v sku_a_id=<sku-a-base62> `
--     -v sku_b_id=<sku-b-base62> `
--     -v result_csv=<loadtest-output/runs/.../order-points-payment-results.csv> `
--     -v expected_stock_a=100 `
--     -v expected_stock_b=350 `
--     -v points_per_item=20 `
--     -f loadtest/sql/verify-order-points-payment-expire.sql

\set ON_ERROR_STOP on
SET client_min_messages TO warning;

\if :{?expected_stock_a}
\else
\set expected_stock_a 100
\endif

\if :{?expected_stock_b}
\else
\set expected_stock_b 350
\endif

\if :{?points_per_item}
\else
\set points_per_item 20
\endif

DROP TABLE IF EXISTS order_points_payment_results;
CREATE TEMP TABLE order_points_payment_results (
    run_id text,
    phase text,
    scenario text,
    attempt integer,
    user_id bigint,
    sku_id text,
    quantity integer,
    order_no text,
    idempotency_key text,
    expire_at text,
    expire_at_epoch_ms bigint,
    target_offset_ms bigint,
    pay_started_at_ms bigint,
    request_fault text,
    delay_ms bigint,
    http_code integer,
    business_code text,
    order_status text,
    payment_type text,
    used_points bigint,
    available_points bigint,
    elapsed_ms bigint,
    success boolean,
    error text
);

\copy order_points_payment_results FROM :'result_csv' CSV HEADER

CREATE TEMP TABLE order_points_verify_params AS
SELECT :'run_id'::text AS run_id,
       :'sku_a_id'::text AS sku_a_id,
       :'sku_b_id'::text AS sku_b_id,
       (:'expected_stock_a')::integer AS expected_stock_a,
       (:'expected_stock_b')::integer AS expected_stock_b,
       (:'points_per_item')::bigint AS points_per_item;

CREATE TEMP TABLE order_points_run_orders AS
SELECT o.*,
       regexp_replace(
           o.idempotency_key,
           '^([0-9]+:)?JMETER-POINTS-' || :'run_id' || '-(.+)-[0-9]+-[0-9]+$',
           '\2'
       ) AS scenario_from_key
FROM trade_order o
WHERE o.idempotency_key LIKE ('JMETER-POINTS-' || :'run_id' || '-%')
   OR o.idempotency_key LIKE ('%:JMETER-POINTS-' || :'run_id' || '-%');

CREATE TEMP TABLE order_points_run_items AS
SELECT o.scenario_from_key,
       i.*
FROM order_points_run_orders o
INNER JOIN trade_order_item i ON i.order_no = o.order_no;

CREATE TEMP TABLE order_points_order_totals AS
SELECT o.order_no,
       o.scenario_from_key,
       COUNT(i.id)::integer AS item_count,
       COALESCE(SUM(i.quantity), 0)::bigint AS total_quantity,
       COALESCE(SUM(i.line_points), 0)::bigint AS total_line_points,
       COALESCE(BOOL_AND(i.point_exchange_enabled), FALSE) AS all_point_exchange_enabled,
       COALESCE(BOOL_AND(i.point_exchange_points = (SELECT points_per_item FROM order_points_verify_params)), FALSE) AS all_point_price_expected,
       COALESCE(BOOL_AND(i.line_points = i.quantity::bigint * (SELECT points_per_item FROM order_points_verify_params)), FALSE) AS all_line_points_expected
FROM order_points_run_orders o
LEFT JOIN trade_order_item i ON i.order_no = o.order_no
GROUP BY o.order_no, o.scenario_from_key;

\echo order_points_payment_result_summary
SELECT phase,
       CASE
           WHEN scenario LIKE 'NORMAL_%' THEN 'NORMAL'
           WHEN scenario LIKE 'CONCURRENT_%' THEN 'CONCURRENT'
           WHEN scenario LIKE 'BOUNDARY_%' THEN 'BOUNDARY'
           WHEN scenario LIKE 'BLOCK_%' THEN 'BLOCK'
           WHEN scenario LIKE 'INSUFFICIENT_%' THEN 'INSUFFICIENT'
           WHEN scenario LIKE 'UNSUPPORTED_%' THEN 'UNSUPPORTED'
           WHEN scenario LIKE 'CLIENT_TIMEOUT_%' THEN 'CLIENT_TIMEOUT'
           ELSE scenario
       END AS scenario_group,
       business_code,
       http_code,
       error,
       COUNT(*) AS row_count,
       MIN(elapsed_ms) AS min_elapsed_ms,
       MAX(elapsed_ms) AS max_elapsed_ms
FROM order_points_payment_results
WHERE run_id = :'run_id'
GROUP BY phase,
         scenario_group,
         business_code,
         http_code,
         error
ORDER BY phase,
         scenario_group,
         business_code,
         http_code,
         error;

\echo order_points_payment_db_order_summary
SELECT status,
       payment_type,
       COUNT(*) AS order_count,
       COALESCE(SUM(used_points), 0) AS used_points,
       COALESCE(SUM(pay_amount_yuan), 0) AS pay_amount_yuan
FROM order_points_run_orders
GROUP BY status, payment_type
ORDER BY status, payment_type;

\echo order_points_payment_scenario_order_summary
SELECT CASE
           WHEN o.scenario_from_key LIKE 'NORMAL_%' THEN 'NORMAL'
           WHEN o.scenario_from_key LIKE 'CONCURRENT_%' THEN 'CONCURRENT'
           WHEN o.scenario_from_key LIKE 'BOUNDARY_%' THEN 'BOUNDARY'
           WHEN o.scenario_from_key LIKE 'BLOCK_%' THEN 'BLOCK'
           WHEN o.scenario_from_key LIKE 'INSUFFICIENT_%' THEN 'INSUFFICIENT'
           WHEN o.scenario_from_key LIKE 'UNSUPPORTED_%' THEN 'UNSUPPORTED'
           WHEN o.scenario_from_key LIKE 'CLIENT_TIMEOUT_%' THEN 'CLIENT_TIMEOUT'
           ELSE o.scenario_from_key
       END AS scenario_group,
       status,
       payment_type,
       COUNT(*) AS order_count,
       COALESCE(SUM(used_points), 0) AS used_points,
       COALESCE(SUM(total_quantity), 0) AS quantity
FROM order_points_run_orders o
LEFT JOIN order_points_order_totals t ON t.order_no = o.order_no
GROUP BY scenario_group, status, payment_type
ORDER BY scenario_group, status, payment_type;

DO $$
DECLARE
    v_run_id text;
    v_sku_a_id text;
    v_sku_b_id text;
    v_sku_a_bytes bytea;
    v_sku_b_bytes bytea;
    v_expected_stock_a integer;
    v_expected_stock_b integer;
    v_points_per_item bigint;
    v_actual bigint;
    v_expected bigint;
    v_bad text;
BEGIN
    SELECT run_id,
           sku_a_id,
           sku_b_id,
           expected_stock_a,
           expected_stock_b,
           points_per_item
    INTO v_run_id,
         v_sku_a_id,
         v_sku_b_id,
         v_expected_stock_a,
         v_expected_stock_b,
         v_points_per_item
    FROM order_points_verify_params
    LIMIT 1;

    v_sku_a_bytes := from_base62(v_sku_a_id);
    v_sku_b_bytes := from_base62(v_sku_b_id);

    IF EXISTS (
        SELECT 1
        FROM order_points_payment_results
        WHERE run_id = v_run_id
          AND scenario LIKE '%CREATE_FAILED'
    ) THEN
        SELECT string_agg(scenario || ':' || COALESCE(business_code, ''), ', ')
        INTO v_bad
        FROM (
            SELECT scenario, business_code
            FROM order_points_payment_results
            WHERE run_id = v_run_id
              AND scenario LIKE '%CREATE_FAILED'
            ORDER BY scenario
            LIMIT 10
        ) s;
        RAISE EXCEPTION 'points-payment flow has create failed rows: %', v_bad;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM order_points_payment_results
        WHERE run_id = v_run_id
    ) THEN
        RAISE EXCEPTION 'points-payment result CSV has no rows for run_id %', v_run_id;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders;
    IF v_actual <> 166 THEN
        RAISE EXCEPTION 'expected 166 run orders, got %', v_actual;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_payment_results
    WHERE run_id = v_run_id;
    IF v_actual <> 1166 THEN
        RAISE EXCEPTION 'expected 1166 result rows, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders o
        INNER JOIN order_points_order_totals t ON t.order_no = o.order_no
        WHERE o.status = 'PAID'
          AND (
              o.payment_type <> 'POINTS'
              OR o.required_points <> t.total_line_points
              OR o.used_points <> o.required_points
              OR o.used_points <> t.total_line_points
              OR o.used_points <= 0
          )
    ) THEN
        SELECT string_agg(order_no || ':' || status || '/' || payment_type || '/required=' || required_points || '/used=' || used_points, ', ')
        INTO v_bad
        FROM (
            SELECT o.order_no, o.status, o.payment_type, o.required_points, o.used_points
            FROM order_points_run_orders o
            INNER JOIN order_points_order_totals t ON t.order_no = o.order_no
            WHERE o.status = 'PAID'
              AND (
                  o.payment_type <> 'POINTS'
                  OR o.required_points <> t.total_line_points
                  OR o.used_points <> o.required_points
                  OR o.used_points <> t.total_line_points
                  OR o.used_points <= 0
              )
            ORDER BY o.order_no
            LIMIT 10
        ) s;
        RAISE EXCEPTION 'paid points orders have invalid payment fields: %', v_bad;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_items i
        WHERE i.scenario_from_key NOT LIKE 'UNSUPPORTED_%'
          AND (
              i.point_exchange_enabled IS DISTINCT FROM TRUE
              OR i.point_exchange_points <> v_points_per_item
              OR i.line_points <> i.quantity::bigint * v_points_per_item
          )
    ) THEN
        SELECT string_agg(order_no || ':' || scenario_from_key, ', ')
        INTO v_bad
        FROM (
            SELECT order_no, scenario_from_key
            FROM order_points_run_items i
            WHERE i.scenario_from_key NOT LIKE 'UNSUPPORTED_%'
              AND (
                  i.point_exchange_enabled IS DISTINCT FROM TRUE
                  OR i.point_exchange_points <> v_points_per_item
                  OR i.line_points <> i.quantity::bigint * v_points_per_item
              )
            ORDER BY order_no
            LIMIT 10
        ) s;
        RAISE EXCEPTION 'eligible order item point snapshots are invalid: %', v_bad;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_items i
        WHERE i.scenario_from_key LIKE 'UNSUPPORTED_%'
          AND (
              i.point_exchange_enabled IS DISTINCT FROM FALSE
              OR i.point_exchange_points <> v_points_per_item
              OR i.line_points <> 0
          )
    ) THEN
        RAISE EXCEPTION 'unsupported order item snapshots must have point_exchange_enabled=false, point_exchange_points expected, and line_points=0';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_items i
        WHERE (
                i.scenario_from_key LIKE 'NORMAL_%'
                OR i.scenario_from_key LIKE 'CONCURRENT_%'
                OR i.scenario_from_key LIKE 'INSUFFICIENT_%'
                OR i.scenario_from_key LIKE 'UNSUPPORTED_%'
              )
          AND i.sku_id <> v_sku_a_bytes
    ) THEN
        RAISE EXCEPTION 'SKU_A scenarios contain non-SKU_A order items';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_items i
        WHERE (
                i.scenario_from_key LIKE 'BOUNDARY_%'
                OR i.scenario_from_key LIKE 'BLOCK_%'
                OR i.scenario_from_key LIKE 'CLIENT_TIMEOUT_%'
              )
          AND i.sku_id <> v_sku_b_bytes
    ) THEN
        RAISE EXCEPTION 'SKU_B scenarios contain non-SKU_B order items';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key LIKE 'NORMAL_%'
      AND status = 'PAID'
      AND payment_type = 'POINTS';
    IF v_actual <> 20 THEN
        RAISE EXCEPTION 'normal scenario expected 20 paid orders, got %', v_actual;
    END IF;

    SELECT COALESCE(SUM(t.total_quantity), 0),
           COALESCE(SUM(o.used_points), 0)
    INTO v_actual, v_expected
    FROM order_points_run_orders o
    INNER JOIN order_points_order_totals t ON t.order_no = o.order_no
    WHERE o.scenario_from_key LIKE 'NORMAL_%'
      AND o.status = 'PAID';
    IF v_actual <> 60 OR v_expected <> 60 * v_points_per_item THEN
        RAISE EXCEPTION 'normal scenario expected quantity=60 and points=%, got quantity=% points=%',
            60 * v_points_per_item, v_actual, v_expected;
    END IF;

    SELECT COUNT(DISTINCT order_no) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key LIKE 'CONCURRENT_%';
    IF v_actual <> 20 THEN
        RAISE EXCEPTION 'concurrent scenario expected 20 distinct orders, got %', v_actual;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_payment_results
    WHERE run_id = v_run_id
      AND scenario LIKE 'CONCURRENT_%';
    IF v_actual <> 1000 THEN
        RAISE EXCEPTION 'concurrent scenario expected 1000 payment result rows, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders o
        WHERE o.scenario_from_key LIKE 'CONCURRENT_%'
          AND (o.status <> 'PAID' OR o.payment_type <> 'POINTS' OR o.used_points <> v_points_per_item)
    ) THEN
        RAISE EXCEPTION 'concurrent scenario final orders must all be PAID/POINTS with one real debit';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_point_account a
        WHERE a.user_id BETWEEN 21 AND 40
          AND a.total_used_points <> v_points_per_item
    ) THEN
        RAISE EXCEPTION 'concurrent users 21-40 must each consume exactly one SKU worth of points';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key LIKE 'BOUNDARY_%';
    IF v_actual <> 50 THEN
        RAISE EXCEPTION 'boundary scenario expected 50 orders, got %', v_actual;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE (scenario_from_key LIKE 'BOUNDARY_M1000_%' OR scenario_from_key LIKE 'BOUNDARY_M200_%')
      AND status = 'PAID'
      AND payment_type = 'POINTS'
      AND used_points = v_points_per_item;
    IF v_actual <> 14 THEN
        RAISE EXCEPTION 'before-expire boundary expected 14 paid orders, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_payment_results r
        INNER JOIN order_points_run_orders o ON o.order_no = r.order_no
        WHERE r.run_id = v_run_id
          AND r.scenario LIKE 'BOUNDARY_0_%'
          AND r.pay_started_at_ms > r.expire_at_epoch_ms
          AND o.status = 'PAID'
    ) THEN
        RAISE EXCEPTION 'boundary 0ms late-start sample unexpectedly became PAID';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE (
                scenario_from_key LIKE 'BOUNDARY_P200_%'
                OR scenario_from_key LIKE 'BOUNDARY_P1000_%'
                OR scenario_from_key LIKE 'BOUNDARY_P10000_%'
                OR scenario_from_key LIKE 'BOUNDARY_P300000_%'
                OR scenario_from_key LIKE 'BOUNDARY_P360000_%'
              )
          AND status = 'PAID'
    ) THEN
        RAISE EXCEPTION 'after-expire boundary scenarios must not become PAID';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key LIKE 'BLOCK_10000_SLEEP_%'
      AND status = 'PAID'
      AND payment_type = 'POINTS'
      AND used_points = v_points_per_item;
    IF v_actual <> 15 THEN
        RAISE EXCEPTION '10s CLOSING/blocking scenario expected 15 paid orders, got %', v_actual;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key LIKE 'BLOCK_70000_SLEEP_%'
      AND status = 'PAID'
      AND payment_type = 'POINTS'
      AND used_points = v_points_per_item;
    IF v_actual <> 15 THEN
        RAISE EXCEPTION '70s CLOSING/blocking scenario expected 15 paid orders, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE (scenario_from_key LIKE 'BLOCK_360000_THROW_AFTER_SLEEP_%'
               OR scenario_from_key LIKE 'BLOCK_360000_THROW_AFTER_DEDUCT_%')
          AND status = 'PAID'
    ) THEN
        RAISE EXCEPTION '360s rollback blocking scenarios must not become PAID';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_payment_results r
        WHERE r.run_id = v_run_id
          AND (r.scenario LIKE 'BLOCK_360000_THROW_AFTER_SLEEP_%'
               OR r.scenario LIKE 'BLOCK_360000_THROW_AFTER_DEDUCT_%')
          AND r.business_code <> 'ORDER_POINTS_PAYMENT_LOADTEST_ROLLBACK'
    ) THEN
        RAISE EXCEPTION '360s rollback blocking scenarios must return ORDER_POINTS_PAYMENT_LOADTEST_ROLLBACK';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE scenario_from_key ~ '^INSUFFICIENT_U(141|142)_Q5$'
          AND status = 'PAID'
    ) THEN
        RAISE EXCEPTION 'insufficient Q5 order must not become PAID';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_payment_results
        WHERE run_id = v_run_id
          AND scenario ~ '^INSUFFICIENT_U(141|142)_Q5$'
          AND business_code <> 'ORDER_POINTS_NOT_ENOUGH'
    ) THEN
        RAISE EXCEPTION 'insufficient Q5 order must return ORDER_POINTS_NOT_ENOUGH';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key ~ '^INSUFFICIENT_U(141|142)_Q5$';
    IF v_actual <> 2 THEN
        RAISE EXCEPTION 'insufficient scenario expected 2 Q5 orders, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_point_account a
        WHERE a.user_id IN (141, 142)
          AND (a.total_used_points <> 0 OR a.available_points <> 80)
    ) THEN
        RAISE EXCEPTION 'users 141-142 must keep 80 available points and consume zero points';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key LIKE 'UNSUPPORTED_%';
    IF v_actual <> 4 THEN
        RAISE EXCEPTION 'unsupported scenario expected 4 orders, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE scenario_from_key LIKE 'UNSUPPORTED_%'
          AND status = 'PAID'
    ) THEN
        RAISE EXCEPTION 'unsupported scenario orders must not become PAID';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_payment_results
        WHERE run_id = v_run_id
          AND scenario LIKE 'UNSUPPORTED_%'
          AND business_code <> 'ORDER_POINTS_PAYMENT_UNAVAILABLE'
    ) THEN
        RAISE EXCEPTION 'unsupported scenario must return ORDER_POINTS_PAYMENT_UNAVAILABLE';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_point_account a
        WHERE a.user_id BETWEEN 143 AND 146
          AND a.total_used_points <> 0
    ) THEN
        RAISE EXCEPTION 'unsupported users 143-146 must not consume points';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key LIKE 'CLIENT_TIMEOUT_%';
    IF v_actual <> 20 THEN
        RAISE EXCEPTION 'client-timeout retry scenario expected 20 orders, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE scenario_from_key LIKE 'CLIENT_TIMEOUT_%'
          AND (status <> 'PAID' OR payment_type <> 'POINTS' OR used_points <> v_points_per_item)
    ) THEN
        RAISE EXCEPTION 'client-timeout retry orders must end as single-debit PAID/POINTS orders';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_point_account a
        WHERE a.user_id BETWEEN 161 AND 180
          AND a.total_used_points <> v_points_per_item
    ) THEN
        RAISE EXCEPTION 'client-timeout retry users 161-180 must each consume exactly one SKU worth of points';
    END IF;

    WITH run_users AS (
        SELECT DISTINCT user_id
        FROM order_points_payment_results
        WHERE run_id = v_run_id
    ),
    paid_orders AS (
        SELECT COALESCE(SUM(used_points), 0) AS paid_used_points
        FROM order_points_run_orders
        WHERE status = 'PAID'
          AND payment_type = 'POINTS'
    )
    SELECT COALESCE(SUM(a.total_used_points), 0),
           (SELECT paid_used_points FROM paid_orders)
    INTO v_actual, v_expected
    FROM user_point_account a
    INNER JOIN run_users u ON u.user_id = a.user_id;

    IF v_actual <> v_expected THEN
        RAISE EXCEPTION 'account total_used_points % does not match paid order used_points %', v_actual, v_expected;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_point_account a
        WHERE a.user_id BETWEEN 1 AND 180
          AND (a.available_points < 0 OR a.total_used_points > 100)
    ) THEN
        RAISE EXCEPTION 'run user account points went negative or above initialized 100 used points';
    END IF;

    IF EXISTS (
        WITH delivery_counts AS (
            SELECT o.order_no,
                   o.status,
                   t.total_quantity,
                   COUNT(d.id) FILTER (WHERE d.status = 'DELIVERED') AS delivered_count
            FROM order_points_run_orders o
            INNER JOIN order_points_order_totals t ON t.order_no = o.order_no
            LEFT JOIN order_card_secret_delivery d ON d.order_no = o.order_no
            GROUP BY o.order_no, o.status, t.total_quantity
        )
        SELECT 1
        FROM delivery_counts
        WHERE status = 'PAID'
          AND delivered_count <> total_quantity
    ) THEN
        RAISE EXCEPTION 'paid points orders did not receive exactly quantity delivered card secrets';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders o
        INNER JOIN order_card_secret_delivery d ON d.order_no = o.order_no
        WHERE o.status <> 'PAID'
          AND d.status = 'DELIVERED'
    ) THEN
        RAISE EXCEPTION 'non-paid points orders received delivered card secrets';
    END IF;

    IF EXISTS (
        SELECT d.card_secret_id
        FROM order_card_secret_delivery d
        INNER JOIN order_points_run_orders o ON o.order_no = d.order_no
        GROUP BY d.card_secret_id
        HAVING COUNT(*) > 1 OR COUNT(DISTINCT d.order_no) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate card secret delivery exists in points-payment run';
    END IF;
END
$$;

\echo order_points_payment_boundary_zero_observation
SELECT r.scenario,
       r.pay_started_at_ms,
       r.expire_at_epoch_ms,
       r.pay_started_at_ms - r.expire_at_epoch_ms AS start_offset_ms,
       r.business_code,
       r.http_code,
       o.status,
       o.payment_type,
       o.used_points
FROM order_points_payment_results r
LEFT JOIN order_points_run_orders o ON o.order_no = r.order_no
WHERE r.run_id = :'run_id'
  AND r.scenario LIKE 'BOUNDARY_0_%'
ORDER BY r.scenario;

\echo order_points_payment_order_detail
SELECT o.scenario_from_key,
       o.user_id,
       o.order_no,
       o.status,
       o.payment_type,
       o.required_points,
       o.used_points,
       o.pay_amount_yuan,
       o.expire_at,
       o.paid_at,
       o.closing_at,
       o.closing_deadline_at,
       o.closed_at,
       t.total_quantity,
       t.total_line_points
FROM order_points_run_orders o
LEFT JOIN order_points_order_totals t ON t.order_no = o.order_no
ORDER BY o.scenario_from_key, o.order_no;

\echo order_points_payment_account_summary
WITH run_users AS (
    SELECT DISTINCT user_id
    FROM order_points_payment_results
    WHERE run_id = :'run_id'
)
SELECT a.user_id,
       a.available_points,
       a.total_earned_points,
       a.total_used_points,
       a.version
FROM user_point_account a
INNER JOIN run_users u ON u.user_id = a.user_id
ORDER BY a.user_id;

\echo order_points_payment_card_secret_summary
SELECT o.status,
       COUNT(DISTINCT o.order_no) AS order_count,
       COALESCE(SUM(t.total_quantity), 0) AS quantity,
       COUNT(d.id) FILTER (WHERE d.status = 'DELIVERED') AS delivered_count
FROM order_points_run_orders o
LEFT JOIN order_points_order_totals t ON t.order_no = o.order_no
LEFT JOIN order_card_secret_delivery d ON d.order_no = o.order_no
GROUP BY o.status
ORDER BY o.status;

\echo order_points_payment_hot_sku_db_baseline
WITH paid_quantity AS (
    SELECT i.sku_id,
           COALESCE(SUM(i.quantity), 0)::bigint AS paid_quantity
    FROM order_points_run_orders o
    INNER JOIN order_points_run_items i ON i.order_no = o.order_no
    WHERE o.status = 'PAID'
      AND o.payment_type = 'POINTS'
    GROUP BY i.sku_id
)
SELECT to_base62(h.sku_id) AS sku_id,
       CASE
           WHEN h.sku_id = from_base62(:'sku_a_id') THEN 'SKU_A'
           WHEN h.sku_id = from_base62(:'sku_b_id') THEN 'SKU_B'
           ELSE 'OTHER'
       END AS sku_label,
       h.stock_quantity,
       h.remaining_quantity AS db_remaining_quantity,
       COALESCE(p.paid_quantity, 0) AS paid_quantity,
       h.stock_quantity - COALESCE(p.paid_quantity, 0) AS expected_redis_remaining,
       h.status,
       h.start_at,
       h.end_at,
       h.version
FROM product_hot_sku h
LEFT JOIN paid_quantity p ON p.sku_id = h.sku_id
WHERE h.sku_id IN (from_base62(:'sku_a_id'), from_base62(:'sku_b_id'))
ORDER BY sku_label;
