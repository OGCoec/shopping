-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" \
--     -v run_id=<run-id> \
--     -v sku_a_id=<sku-a-base62> \
--     -v sku_b_id=<sku-b-base62> \
--     -v result_csv=<loadtest-output/runs/.../order-points-payment-results.csv> \
--     -v expected_stock_a=100 \
--     -v expected_stock_b=350 \
--     -v points_per_item=20 \
--     -f loadtest/sql/verify-order-points-payment-negative-no-cancel.sql

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
       COALESCE(SUM(i.line_points), 0)::bigint AS total_line_points
FROM order_points_run_orders o
LEFT JOIN trade_order_item i ON i.order_no = o.order_no
GROUP BY o.order_no, o.scenario_from_key;

\echo negative_no_cancel_result_summary
SELECT phase,
       CASE
           WHEN scenario LIKE 'UNSUPPORTED_%' THEN 'UNSUPPORTED'
           WHEN scenario LIKE 'INSUFFICIENT_%' THEN 'INSUFFICIENT'
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
ORDER BY phase, scenario_group, business_code, http_code, error;

\echo negative_no_cancel_order_summary
SELECT CASE
           WHEN o.scenario_from_key LIKE 'UNSUPPORTED_%' THEN 'UNSUPPORTED'
           WHEN o.scenario_from_key LIKE 'INSUFFICIENT_%' THEN 'INSUFFICIENT'
           ELSE o.scenario_from_key
       END AS scenario_group,
       o.status,
       o.payment_type,
       COUNT(*) AS order_count,
       COALESCE(SUM(o.used_points), 0) AS used_points,
       COALESCE(SUM(t.total_quantity), 0) AS total_quantity,
       COALESCE(SUM(t.total_line_points), 0) AS total_line_points
FROM order_points_run_orders o
LEFT JOIN order_points_order_totals t ON t.order_no = o.order_no
GROUP BY scenario_group, o.status, o.payment_type
ORDER BY scenario_group, o.status, o.payment_type;

DO $$
DECLARE
    v_run_id text;
    v_sku_a_id text;
    v_sku_a_bytes bytea;
    v_points_per_item bigint;
    v_actual bigint;
    v_bad text;
BEGIN
    SELECT run_id,
           sku_a_id,
           points_per_item
    INTO v_run_id,
         v_sku_a_id,
         v_points_per_item
    FROM order_points_verify_params
    LIMIT 1;

    v_sku_a_bytes := from_base62(v_sku_a_id);

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
        RAISE EXCEPTION 'negative no-cancel flow has create failed rows: %', v_bad;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_payment_results
    WHERE run_id = v_run_id;
    IF v_actual <> 40 THEN
        RAISE EXCEPTION 'negative no-cancel result rows expected 40, got %', v_actual;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_payment_results
    WHERE run_id = v_run_id
      AND scenario ~ '^UNSUPPORTED_U(14[3-9]|15[0-9]|16[0-2])$';
    IF v_actual <> 20 THEN
        RAISE EXCEPTION 'unsupported result rows expected 20, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_payment_results
        WHERE run_id = v_run_id
          AND scenario ~ '^UNSUPPORTED_U(14[3-9]|15[0-9]|16[0-2])$'
          AND business_code <> 'ORDER_POINTS_PAYMENT_UNAVAILABLE'
    ) THEN
        RAISE EXCEPTION 'unsupported negative rows must return ORDER_POINTS_PAYMENT_UNAVAILABLE';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_payment_results
    WHERE run_id = v_run_id
      AND scenario ~ '^INSUFFICIENT_U(18[1-9]|19[0-9]|200)_Q6$';
    IF v_actual <> 20 THEN
        RAISE EXCEPTION 'insufficient result rows expected 20, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_payment_results
        WHERE run_id = v_run_id
          AND scenario ~ '^INSUFFICIENT_U(18[1-9]|19[0-9]|200)_Q6$'
          AND business_code <> 'ORDER_POINTS_NOT_ENOUGH'
    ) THEN
        RAISE EXCEPTION 'insufficient negative rows must return ORDER_POINTS_NOT_ENOUGH';
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key ~ '^UNSUPPORTED_U(14[3-9]|15[0-9]|16[0-2])$';
    IF v_actual <> 20 THEN
        RAISE EXCEPTION 'unsupported orders expected 20, got %', v_actual;
    END IF;

    SELECT COUNT(*) INTO v_actual
    FROM order_points_run_orders
    WHERE scenario_from_key ~ '^INSUFFICIENT_U(18[1-9]|19[0-9]|200)_Q6$';
    IF v_actual <> 20 THEN
        RAISE EXCEPTION 'insufficient orders expected 20, got %', v_actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE status = 'PAID'
    ) THEN
        RAISE EXCEPTION 'negative no-cancel orders must not become PAID';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE status = 'CANCELLED'
    ) THEN
        RAISE EXCEPTION 'negative no-cancel orders must not be CANCELLED by the script';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_orders
        WHERE status <> 'CLOSED'
           OR payment_type <> 'UNPAID'
           OR used_points <> 0
    ) THEN
        SELECT string_agg(order_no || ':' || status || '/' || payment_type || '/used=' || used_points, ', ')
        INTO v_bad
        FROM (
            SELECT order_no, status, payment_type, used_points
            FROM order_points_run_orders
            WHERE status <> 'CLOSED'
               OR payment_type <> 'UNPAID'
               OR used_points <> 0
            ORDER BY order_no
            LIMIT 10
        ) s;
        RAISE EXCEPTION 'negative no-cancel orders must naturally close as CLOSED/UNPAID/used_points=0 after settle: %', v_bad;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_items
        WHERE sku_id <> v_sku_a_bytes
    ) THEN
        RAISE EXCEPTION 'negative no-cancel scenarios must only use SKU_A';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_items
        WHERE scenario_from_key ~ '^UNSUPPORTED_U(14[3-9]|15[0-9]|16[0-2])$'
          AND (
              quantity <> 1
              OR point_exchange_enabled IS DISTINCT FROM FALSE
              OR point_exchange_points <> v_points_per_item
              OR line_points <> 0
          )
    ) THEN
        RAISE EXCEPTION 'unsupported snapshots must be quantity=1, point_exchange_enabled=false, point_exchange_points expected, line_points=0';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_points_run_items
        WHERE scenario_from_key ~ '^INSUFFICIENT_U(18[1-9]|19[0-9]|200)_Q6$'
          AND (
              quantity <> 6
              OR point_exchange_enabled IS DISTINCT FROM TRUE
              OR point_exchange_points <> v_points_per_item
              OR line_points <> 6 * v_points_per_item
          )
    ) THEN
        RAISE EXCEPTION 'insufficient snapshots must be quantity=6, point_exchange_points expected, line_points=120';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_point_account a
        WHERE a.user_id BETWEEN 143 AND 162
          AND (a.available_points <> 100 OR a.total_earned_points <> 100 OR a.total_used_points <> 0)
    ) THEN
        RAISE EXCEPTION 'unsupported users 143-162 must keep 100 available points and consume zero points';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_point_account a
        WHERE a.user_id BETWEEN 181 AND 200
          AND (a.available_points <> 100 OR a.total_earned_points <> 100 OR a.total_used_points <> 0)
    ) THEN
        RAISE EXCEPTION 'insufficient users 181-200 must keep 100 available points and consume zero points';
    END IF;
END
$$;

\echo negative_no_cancel_order_detail
SELECT o.scenario_from_key,
       o.order_no,
       o.user_id,
       o.status,
       o.payment_type,
       o.used_points,
       o.pay_amount_yuan,
       o.created_at,
       o.expire_at,
       o.closing_at,
       o.closed_at,
       t.total_quantity,
       t.total_line_points
FROM order_points_run_orders o
LEFT JOIN order_points_order_totals t ON t.order_no = o.order_no
ORDER BY o.scenario_from_key, o.order_no;

\echo negative_no_cancel_item_detail
SELECT i.scenario_from_key,
       i.order_no,
       i.quantity,
       i.point_exchange_enabled,
       i.point_exchange_points,
       i.line_points
FROM order_points_run_items i
ORDER BY i.scenario_from_key, i.order_no;

\echo negative_no_cancel_account_summary
SELECT a.user_id,
       a.available_points,
       a.total_earned_points,
       a.total_used_points,
       a.version
FROM user_point_account a
WHERE a.user_id BETWEEN 143 AND 162
   OR a.user_id BETWEEN 181 AND 200
ORDER BY a.user_id;
