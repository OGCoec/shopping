-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" `
--     -v ON_ERROR_STOP=1 `
--     -v run_id=<run-id> `
--     -v input_csv=<loadtest-output/order-duplicate-callback-card-secret-input.csv> `
--     -v result_csv=<loadtest-output/runs/.../order-duplicate-callback-card-secret-results.csv> `
--     -f loadtest/sql/verify-order-duplicate-callback-card-secret.sql

CREATE TEMP TABLE duplicate_callback_input (
    run_id text,
    order_no text,
    external_trade_no text,
    paid_amount_yuan numeric,
    payment_provider text,
    expected_quantity integer,
    duplicate_index integer
);

CREATE TEMP TABLE duplicate_callback_result (
    run_id text,
    order_no text,
    external_trade_no text,
    paid_amount_yuan numeric,
    payment_provider text,
    expected_quantity integer,
    duplicate_index integer,
    callback_no text,
    accepted_status text,
    business_code text,
    http_code text,
    success boolean,
    response_body text,
    callback_started_at_ms bigint
);

CREATE TEMP TABLE duplicate_callback_failures (
    failure_type text NOT NULL,
    order_no text,
    external_trade_no text,
    detail text
);

\copy duplicate_callback_input FROM :'input_csv' CSV HEADER
\copy duplicate_callback_result FROM :'result_csv' CSV HEADER

\echo duplicate_callback_attempt_summary
WITH input_rows AS (
    SELECT *
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
),
result_rows AS (
    SELECT *
    FROM duplicate_callback_result
    WHERE run_id = :'run_id'
)
SELECT COUNT(*) FILTER (WHERE source = 'input') AS input_count,
       COUNT(*) FILTER (WHERE source = 'result') AS result_count,
       COUNT(*) FILTER (
           WHERE source = 'result'
             AND http_code = '200'
             AND business_code = 'ORDER_PAYMENT_CALLBACK_RECEIVED'
             AND success IS TRUE
       ) AS accepted_result_count,
       COUNT(DISTINCT order_no) FILTER (WHERE source = 'input') AS order_count,
       COALESCE(SUM(expected_quantity) FILTER (WHERE source = 'input' AND duplicate_index = 1), 0) AS expected_delivery_count
FROM (
    SELECT 'input' AS source, order_no, expected_quantity, duplicate_index, NULL::text AS http_code, NULL::text AS business_code, NULL::boolean AS success
    FROM input_rows
    UNION ALL
    SELECT 'result' AS source, order_no, expected_quantity, duplicate_index, http_code, business_code, success
    FROM result_rows
) unioned;

\echo duplicate_callback_inbox_status_counts
SELECT i.status,
       i.result_outcome,
       COUNT(*) AS inbox_count
FROM payment_callback_inbox i
INNER JOIN (
    SELECT DISTINCT order_no,
           external_trade_no
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
) target ON target.order_no = i.order_no
        AND target.external_trade_no = i.external_trade_no
GROUP BY i.status, i.result_outcome
ORDER BY i.status, i.result_outcome;

\echo duplicate_callback_delivery_counts
WITH target_orders AS (
    SELECT order_no,
           MIN(external_trade_no) AS external_trade_no,
           MAX(expected_quantity) AS expected_quantity
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
    GROUP BY order_no
)
SELECT target.order_no,
       target.expected_quantity,
       COUNT(delivery.id) FILTER (WHERE delivery.status = 'DELIVERED') AS delivered_count,
       COUNT(inventory.id) FILTER (WHERE inventory.status = 'SOLD' AND inventory.order_no = target.order_no) AS sold_inventory_count
FROM target_orders target
LEFT JOIN order_card_secret_delivery delivery
       ON delivery.order_no = target.order_no
LEFT JOIN card_secret_inventory inventory
       ON inventory.id = delivery.card_secret_id
GROUP BY target.order_no, target.expected_quantity
ORDER BY target.order_no;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'result_missing',
       input.order_no,
       input.external_trade_no,
       'duplicate_index=' || input.duplicate_index
FROM duplicate_callback_input input
LEFT JOIN duplicate_callback_result result
       ON result.run_id = input.run_id
      AND result.order_no = input.order_no
      AND result.external_trade_no = input.external_trade_no
      AND result.duplicate_index = input.duplicate_index
WHERE input.run_id = :'run_id'
  AND result.order_no IS NULL;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'result_duplicate',
       input.order_no,
       input.external_trade_no,
       'duplicate_index=' || input.duplicate_index || ', result_rows=' || COUNT(*)
FROM duplicate_callback_input input
INNER JOIN duplicate_callback_result result
        ON result.run_id = input.run_id
       AND result.order_no = input.order_no
       AND result.external_trade_no = input.external_trade_no
       AND result.duplicate_index = input.duplicate_index
WHERE input.run_id = :'run_id'
GROUP BY input.order_no, input.external_trade_no, input.duplicate_index
HAVING COUNT(*) <> 1;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'result_unexpected',
       result.order_no,
       result.external_trade_no,
       'duplicate_index=' || result.duplicate_index
FROM duplicate_callback_result result
LEFT JOIN duplicate_callback_input input
       ON input.run_id = result.run_id
      AND input.order_no = result.order_no
      AND input.external_trade_no = result.external_trade_no
      AND input.duplicate_index = result.duplicate_index
WHERE result.run_id = :'run_id'
  AND input.order_no IS NULL;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'http_or_business_failure',
       result.order_no,
       result.external_trade_no,
       'duplicate_index=' || result.duplicate_index
           || ', http_code=' || COALESCE(result.http_code, '')
           || ', business_code=' || COALESCE(result.business_code, '')
           || ', success=' || COALESCE(result.success::text, '')
FROM duplicate_callback_result result
WHERE result.run_id = :'run_id'
  AND (
      result.http_code <> '200'
      OR result.business_code <> 'ORDER_PAYMENT_CALLBACK_RECEIVED'
      OR result.success IS NOT TRUE
  );

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'callback_no_missing',
       result.order_no,
       result.external_trade_no,
       'duplicate_index=' || result.duplicate_index || ', accepted_status=' || COALESCE(result.accepted_status, '')
FROM duplicate_callback_result result
WHERE result.run_id = :'run_id'
  AND result.http_code = '200'
  AND result.business_code = 'ORDER_PAYMENT_CALLBACK_RECEIVED'
  AND NULLIF(result.callback_no, '') IS NULL;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_orders AS (
    SELECT order_no,
           MIN(external_trade_no) AS external_trade_no
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
    GROUP BY order_no
)
SELECT 'order_row_count',
       target.order_no,
       target.external_trade_no,
       'order_rows=' || COUNT(order_row.id)
FROM target_orders target
LEFT JOIN trade_order order_row ON order_row.order_no = target.order_no
GROUP BY target.order_no, target.external_trade_no
HAVING COUNT(order_row.id) <> 1;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_orders AS (
    SELECT order_no,
           MIN(external_trade_no) AS external_trade_no
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
    GROUP BY order_no
)
SELECT 'order_status_not_paid',
       target.order_no,
       target.external_trade_no,
       'status=' || COALESCE(order_row.status, 'NULL')
FROM target_orders target
LEFT JOIN trade_order order_row ON order_row.order_no = target.order_no
WHERE COALESCE(order_row.status, '') <> 'PAID';

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_orders AS (
    SELECT order_no,
           MIN(external_trade_no) AS external_trade_no,
           MAX(expected_quantity) AS expected_quantity
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
    GROUP BY order_no
)
SELECT 'order_item_quantity_mismatch',
       target.order_no,
       target.external_trade_no,
       'expected=' || target.expected_quantity || ', actual=' || COALESCE(SUM(item.quantity), 0)
FROM target_orders target
LEFT JOIN trade_order_item item ON item.order_no = target.order_no
GROUP BY target.order_no, target.external_trade_no, target.expected_quantity
HAVING COALESCE(SUM(item.quantity), 0) <> target.expected_quantity;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_pairs AS (
    SELECT DISTINCT order_no,
           external_trade_no
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
)
SELECT 'inbox_row_count',
       target.order_no,
       target.external_trade_no,
       'inbox_rows=' || COUNT(inbox.id)
FROM target_pairs target
LEFT JOIN payment_callback_inbox inbox
       ON inbox.order_no = target.order_no
      AND inbox.external_trade_no = target.external_trade_no
GROUP BY target.order_no, target.external_trade_no
HAVING COUNT(inbox.id) <> 1;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_pairs AS (
    SELECT DISTINCT order_no,
           external_trade_no
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
)
SELECT 'inbox_final_state',
       target.order_no,
       target.external_trade_no,
       'status=' || COALESCE(inbox.status, 'NULL')
           || ', outcome=' || COALESCE(inbox.result_outcome, 'NULL')
           || ', order_status=' || COALESCE(inbox.result_order_status, 'NULL')
           || ', refund_no=' || COALESCE(inbox.refund_no, 'NULL')
FROM target_pairs target
LEFT JOIN payment_callback_inbox inbox
       ON inbox.order_no = target.order_no
      AND inbox.external_trade_no = target.external_trade_no
WHERE inbox.id IS NULL
   OR inbox.status <> 'PROCESSED'
   OR inbox.result_outcome NOT IN ('PAID', 'PAID_IDEMPOTENT')
   OR inbox.result_order_status <> 'PAID'
   OR inbox.refund_no IS NOT NULL;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'callback_no_not_idempotent',
       result.order_no,
       result.external_trade_no,
       'distinct_callback_no=' || COUNT(DISTINCT NULLIF(result.callback_no, ''))
FROM duplicate_callback_result result
WHERE result.run_id = :'run_id'
GROUP BY result.order_no, result.external_trade_no
HAVING COUNT(DISTINCT NULLIF(result.callback_no, '')) <> 1;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_orders AS (
    SELECT order_no,
           MIN(external_trade_no) AS external_trade_no,
           MAX(expected_quantity) AS expected_quantity
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
    GROUP BY order_no
)
SELECT 'delivery_quantity_mismatch',
       target.order_no,
       target.external_trade_no,
       'expected=' || target.expected_quantity
           || ', delivered=' || COUNT(delivery.id) FILTER (WHERE delivery.status = 'DELIVERED')
FROM target_orders target
LEFT JOIN order_card_secret_delivery delivery
       ON delivery.order_no = target.order_no
GROUP BY target.order_no, target.external_trade_no, target.expected_quantity
HAVING COUNT(delivery.id) FILTER (WHERE delivery.status = 'DELIVERED') <> target.expected_quantity;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_orders AS (
    SELECT order_no,
           MIN(external_trade_no) AS external_trade_no,
           MAX(expected_quantity) AS expected_quantity
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
    GROUP BY order_no
)
SELECT 'card_secret_over_delivery',
       target.order_no,
       target.external_trade_no,
       'expected=' || target.expected_quantity
           || ', delivered=' || COUNT(delivery.id) FILTER (WHERE delivery.status = 'DELIVERED')
FROM target_orders target
LEFT JOIN order_card_secret_delivery delivery
       ON delivery.order_no = target.order_no
GROUP BY target.order_no, target.external_trade_no, target.expected_quantity
HAVING COUNT(delivery.id) FILTER (WHERE delivery.status = 'DELIVERED') > target.expected_quantity;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_orders AS (
    SELECT order_no,
           MIN(external_trade_no) AS external_trade_no,
           MAX(expected_quantity) AS expected_quantity
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
    GROUP BY order_no
),
joined AS (
    SELECT target.order_no,
           target.external_trade_no,
           target.expected_quantity,
           delivery.card_secret_id,
           inventory.id AS inventory_id,
           inventory.status AS inventory_status,
           inventory.order_no AS inventory_order_no
    FROM target_orders target
    LEFT JOIN order_card_secret_delivery delivery
           ON delivery.order_no = target.order_no
          AND delivery.status = 'DELIVERED'
    LEFT JOIN card_secret_inventory inventory
           ON inventory.id = delivery.card_secret_id
)
SELECT 'sold_inventory_mismatch',
       order_no,
       external_trade_no,
       'expected=' || expected_quantity
           || ', delivered=' || COUNT(card_secret_id)
           || ', sold_inventory=' || COUNT(inventory_id) FILTER (WHERE inventory_status = 'SOLD' AND inventory_order_no = order_no)
FROM joined
GROUP BY order_no, external_trade_no, expected_quantity
HAVING COUNT(card_secret_id) <> expected_quantity
    OR COUNT(inventory_id) FILTER (WHERE inventory_status = 'SOLD' AND inventory_order_no = order_no) <> expected_quantity;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'duplicate_card_secret_delivery_target_orders',
       MIN(delivery.order_no),
       NULL,
       'card_secret_id=' || to_base62(delivery.card_secret_id)
           || ', delivery_rows=' || COUNT(*)
           || ', order_count=' || COUNT(DISTINCT delivery.order_no)
FROM order_card_secret_delivery delivery
WHERE delivery.order_no IN (
    SELECT DISTINCT order_no
    FROM duplicate_callback_input
    WHERE run_id = :'run_id'
)
GROUP BY delivery.card_secret_id
HAVING COUNT(*) > 1 OR COUNT(DISTINCT delivery.order_no) > 1;

INSERT INTO duplicate_callback_failures (failure_type, order_no, external_trade_no, detail)
WITH target_cards AS (
    SELECT DISTINCT delivery.card_secret_id
    FROM order_card_secret_delivery delivery
    WHERE delivery.order_no IN (
        SELECT DISTINCT order_no
        FROM duplicate_callback_input
        WHERE run_id = :'run_id'
    )
)
SELECT 'duplicate_card_secret_delivery_global',
       MIN(delivery.order_no),
       NULL,
       'card_secret_id=' || to_base62(delivery.card_secret_id)
           || ', delivery_rows=' || COUNT(*)
           || ', order_count=' || COUNT(DISTINCT delivery.order_no)
FROM order_card_secret_delivery delivery
INNER JOIN target_cards target ON target.card_secret_id = delivery.card_secret_id
GROUP BY delivery.card_secret_id
HAVING COUNT(*) > 1 OR COUNT(DISTINCT delivery.order_no) > 1;

\echo duplicate_callback_failure_details
SELECT *
FROM duplicate_callback_failures
ORDER BY failure_type, order_no, external_trade_no, detail;

\echo duplicate_callback_verification_result
SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       COUNT(*) AS failure_count
FROM duplicate_callback_failures;

DO $$
DECLARE
    failure_count integer;
BEGIN
    SELECT COUNT(*) INTO failure_count
    FROM duplicate_callback_failures;

    IF failure_count > 0 THEN
        RAISE EXCEPTION 'duplicate callback card-secret verification failed: % failure rows', failure_count;
    END IF;
END $$;
