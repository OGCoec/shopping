-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" `
--     -v run_id=<run-id> `
--     -v success_csv=<loadtest-output/runs/.../order-soft-close-success-orders.csv> `
--     -v card_secret_result_csv=<loadtest-output/runs/.../order-card-secret-results.csv> `
--     -v expected_stock=50 `
--     -v pay_now_count=4 `
--     -v closing_callback_count=4 `
--     -v closing_user_pay_negative_count=4 `
--     -v closed_callback_negative_count=4 `
--     -f loadtest/sql/verify-order-soft-close-payment.sql

CREATE TEMP TABLE soft_close_success_orders (
    run_id text,
    user_id bigint,
    sku_id text,
    quantity integer,
    order_no text,
    order_status text,
    success_index integer,
    payment_scenario text,
    business_code text,
    create_started_at_ms bigint,
    expire_at text,
    expire_at_epoch_ms bigint,
    boundary_record_target_ms bigint,
    closing_callback_target_ms bigint,
    closed_callback_target_ms bigint
);

CREATE TEMP TABLE order_card_secret_api_results (
    run_id text,
    user_id bigint,
    sku_id text,
    quantity integer,
    order_no text,
    success_index integer,
    payment_scenario text,
    expected_paid boolean,
    http_code integer,
    business_code text,
    delivery_status text,
    required_count integer,
    delivered_count integer,
    secret_count integer,
    cache_control_no_store boolean,
    query_started_at_ms bigint,
    elapsed_ms bigint,
    success boolean
);

\copy soft_close_success_orders FROM :'success_csv' CSV HEADER
\copy order_card_secret_api_results FROM :'card_secret_result_csv' CSV HEADER

\echo order_soft_close_run_summary
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-soft-close-' || :'run_id' || '-%')
)
SELECT COUNT(*) AS order_count,
       COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT') AS pending_payment_count,
       COUNT(*) FILTER (WHERE status = 'CLOSING') AS closing_count,
       COUNT(*) FILTER (WHERE status = 'PAID') AS paid_count,
       COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
       COUNT(*) FILTER (WHERE status = 'CLOSED') AS closed_count,
       MIN(created_at) AS first_created_at,
       MAX(created_at) AS last_created_at
FROM run_orders;

\echo order_soft_close_status_counts
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-soft-close-' || :'run_id' || '-%')
)
SELECT status, COUNT(*) AS status_count
FROM run_orders
GROUP BY status
ORDER BY status;

\echo order_soft_close_jmeter_success_scenario_counts
SELECT payment_scenario,
       COUNT(*) AS success_order_count,
       COALESCE(SUM(quantity), 0) AS scenario_quantity
FROM soft_close_success_orders
WHERE run_id = :'run_id'
GROUP BY payment_scenario
ORDER BY MIN(success_index);

\echo order_soft_close_group_shortage
WITH expected AS (
    SELECT 'PAY_NOW' AS payment_scenario, CAST(:'pay_now_count' AS integer) AS expected_count
    UNION ALL
    SELECT 'CLOSING_CALLBACK', CAST(:'closing_callback_count' AS integer)
    UNION ALL
    SELECT 'CLOSING_USER_PAY_NEGATIVE', CAST(:'closing_user_pay_negative_count' AS integer)
    UNION ALL
    SELECT 'CLOSED_CALLBACK_NEGATIVE', CAST(:'closed_callback_negative_count' AS integer)
),
actual AS (
    SELECT payment_scenario, COUNT(*) AS actual_count
    FROM soft_close_success_orders
    WHERE run_id = :'run_id'
    GROUP BY payment_scenario
)
SELECT e.payment_scenario,
       e.expected_count,
       COALESCE(a.actual_count, 0) AS actual_count,
       CASE
           WHEN COALESCE(a.actual_count, 0) >= e.expected_count THEN 'OK'
           ELSE 'SHORTAGE'
       END AS result
FROM expected e
LEFT JOIN actual a ON a.payment_scenario = e.payment_scenario
ORDER BY e.payment_scenario;

\echo order_soft_close_quantity_distribution
SELECT quantity,
       COUNT(*) AS request_success_count
FROM soft_close_success_orders
WHERE run_id = :'run_id'
GROUP BY quantity
ORDER BY quantity;

\echo order_soft_close_inventory_safety_from_success_csv
SELECT COUNT(*) AS success_order_count,
       COALESCE(SUM(quantity), 0) AS ordered_quantity,
       CAST(:'expected_stock' AS integer) AS expected_stock,
       CASE
           WHEN COALESCE(SUM(quantity), 0) <= CAST(:'expected_stock' AS integer) THEN 'OK'
           ELSE 'OVERSELL'
       END AS safety_result
FROM soft_close_success_orders
WHERE run_id = :'run_id';

\echo order_soft_close_inventory_safety_from_db_items
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-soft-close-' || :'run_id' || '-%')
)
SELECT to_base62(i.sku_id) AS sku_id_base62,
       COUNT(*) AS order_count,
       COALESCE(SUM(i.quantity), 0) AS ordered_quantity,
       h.stock_quantity AS configured_hot_stock,
       CASE
           WHEN h.stock_quantity IS NULL THEN 'HOT_SKU_NOT_FOUND'
           WHEN COALESCE(SUM(i.quantity), 0) <= h.stock_quantity THEN 'OK'
           ELSE 'OVERSELL'
       END AS safety_result
FROM run_orders o
INNER JOIN trade_order_item i ON i.order_no = o.order_no
LEFT JOIN product_hot_sku h ON h.sku_id = i.sku_id
GROUP BY i.sku_id, h.stock_quantity
ORDER BY ordered_quantity DESC, sku_id_base62;

\echo order_soft_close_status_by_payment_scenario
SELECT s.payment_scenario,
       o.status,
       COUNT(*) AS order_count,
       COALESCE(SUM(s.quantity), 0) AS quantity
FROM soft_close_success_orders s
LEFT JOIN trade_order o ON o.order_no = s.order_no
WHERE s.run_id = :'run_id'
GROUP BY s.payment_scenario, o.status
ORDER BY MIN(s.success_index), o.status;

\echo order_soft_close_expectation_failures
WITH expected AS (
    SELECT s.order_no,
           s.user_id,
           s.quantity,
           s.success_index,
           s.payment_scenario,
           CASE
               WHEN s.payment_scenario IN ('PAY_NOW', 'CLOSING_CALLBACK', 'BOUNDARY_DELAYED_CALLBACK') THEN 'PAID'
               WHEN s.payment_scenario IN ('CLOSING_USER_PAY_NEGATIVE', 'CLOSED_CALLBACK_NEGATIVE') THEN 'CLOSED'
               ELSE NULL
           END AS expected_status,
           o.status AS actual_status
    FROM soft_close_success_orders s
    LEFT JOIN trade_order o ON o.order_no = s.order_no
    WHERE s.run_id = :'run_id'
)
SELECT *
FROM expected
WHERE expected_status IS NOT NULL
  AND COALESCE(actual_status, '') <> expected_status
ORDER BY success_index;

\echo order_soft_close_terminal_status_check
WITH joined AS (
    SELECT s.payment_scenario,
           o.status,
           s.quantity
    FROM soft_close_success_orders s
    LEFT JOIN trade_order o ON o.order_no = s.order_no
    WHERE s.run_id = :'run_id'
)
SELECT COUNT(*) FILTER (WHERE status = 'PAID') AS paid_order_count,
       COALESCE(SUM(quantity) FILTER (WHERE status = 'PAID'), 0) AS paid_quantity,
       COUNT(*) FILTER (WHERE status = 'CLOSED') AS closed_order_count,
       COALESCE(SUM(quantity) FILTER (WHERE status = 'CLOSED'), 0) AS closed_quantity,
       COUNT(*) FILTER (WHERE status IN ('PENDING_PAYMENT', 'CLOSING')) AS non_terminal_order_count
FROM joined;

\echo order_soft_close_duplicate_user_sku
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-soft-close-' || :'run_id' || '-%')
)
SELECT o.user_id,
       to_base62(i.sku_id) AS sku_id_base62,
       COUNT(*) AS duplicate_count
FROM run_orders o
INNER JOIN trade_order_item i ON i.order_no = o.order_no
GROUP BY o.user_id, i.sku_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, o.user_id, sku_id_base62;

\echo order_soft_close_closed_callback_refunds
SELECT pr.status AS refund_status,
       pr.reason_code,
       COUNT(*) AS refund_count,
       COALESCE(SUM(pr.refund_amount_yuan), 0) AS refund_amount_yuan
FROM soft_close_success_orders s
INNER JOIN payment_callback_inbox c
        ON c.order_no = s.order_no
       AND c.external_trade_no = ('JMETER-CLOSED-CALLBACK-' || :'run_id' || '-' || s.order_no)
LEFT JOIN payment_refund_record pr ON pr.refund_no = c.refund_no
WHERE s.run_id = :'run_id'
  AND s.payment_scenario = 'CLOSED_CALLBACK_NEGATIVE'
GROUP BY pr.status, pr.reason_code
ORDER BY pr.status, pr.reason_code;

\echo order_soft_close_card_secret_paid_delivery_mismatches
WITH paid_orders AS (
    SELECT s.order_no,
           s.quantity,
           s.payment_scenario
    FROM soft_close_success_orders s
    INNER JOIN trade_order o ON o.order_no = s.order_no
    WHERE s.run_id = :'run_id'
      AND o.status = 'PAID'
)
SELECT p.payment_scenario,
       p.order_no,
       p.quantity AS expected_count,
       COUNT(d.id) AS delivered_count
FROM paid_orders p
LEFT JOIN order_card_secret_delivery d
       ON d.order_no = p.order_no
      AND d.status = 'DELIVERED'
GROUP BY p.payment_scenario, p.order_no, p.quantity
HAVING COUNT(d.id) <> p.quantity
ORDER BY p.payment_scenario, p.order_no;

\echo order_soft_close_card_secret_non_paid_delivery_failures
WITH non_paid_orders AS (
    SELECT s.order_no,
           s.payment_scenario,
           o.status
    FROM soft_close_success_orders s
    INNER JOIN trade_order o ON o.order_no = s.order_no
    WHERE s.run_id = :'run_id'
      AND o.status <> 'PAID'
)
SELECT n.payment_scenario,
       n.order_no,
       n.status,
       COUNT(d.id) AS delivered_count
FROM non_paid_orders n
LEFT JOIN order_card_secret_delivery d ON d.order_no = n.order_no
GROUP BY n.payment_scenario, n.order_no, n.status
HAVING COUNT(d.id) > 0
ORDER BY n.payment_scenario, n.order_no;

\echo order_soft_close_card_secret_api_summary
SELECT payment_scenario,
       expected_paid,
       business_code,
       delivery_status,
       COUNT(*) AS query_count,
       COALESCE(SUM(required_count), 0) AS required_count,
       COALESCE(SUM(delivered_count), 0) AS delivered_count,
       COALESCE(SUM(secret_count), 0) AS secret_count,
       COUNT(*) FILTER (WHERE cache_control_no_store) AS no_store_count,
       COUNT(*) FILTER (WHERE success) AS jmeter_success_count
FROM order_card_secret_api_results
WHERE run_id = :'run_id'
GROUP BY payment_scenario, expected_paid, business_code, delivery_status
ORDER BY payment_scenario, expected_paid, business_code, delivery_status;

\echo order_soft_close_card_secret_api_missing_results
SELECT s.payment_scenario,
       s.order_no,
       s.quantity
FROM soft_close_success_orders s
LEFT JOIN order_card_secret_api_results r
       ON r.run_id = s.run_id
      AND r.order_no = s.order_no
WHERE s.run_id = :'run_id'
  AND r.order_no IS NULL
ORDER BY s.success_index;

\echo order_soft_close_card_secret_api_paid_failures
SELECT r.payment_scenario,
       r.order_no,
       s.quantity AS expected_quantity,
       r.http_code,
       r.business_code,
       r.delivery_status,
       r.required_count,
       r.delivered_count,
       r.secret_count,
       r.cache_control_no_store,
       r.success
FROM order_card_secret_api_results r
INNER JOIN soft_close_success_orders s
        ON s.run_id = r.run_id
       AND s.order_no = r.order_no
WHERE r.run_id = :'run_id'
  AND s.payment_scenario IN ('PAY_NOW', 'CLOSING_CALLBACK', 'BOUNDARY_DELAYED_CALLBACK')
  AND (
      r.business_code <> 'ORDER_CARD_SECRET_LIST_OK'
      OR r.delivery_status <> 'DELIVERED'
      OR r.required_count <> s.quantity
      OR r.delivered_count <> s.quantity
      OR r.secret_count <> s.quantity
      OR r.cache_control_no_store IS DISTINCT FROM true
      OR r.success IS DISTINCT FROM true
  )
ORDER BY s.success_index;

\echo order_soft_close_card_secret_api_non_paid_failures
SELECT r.payment_scenario,
       r.order_no,
       s.quantity,
       r.http_code,
       r.business_code,
       r.delivery_status,
       r.required_count,
       r.delivered_count,
       r.secret_count,
       r.cache_control_no_store,
       r.success
FROM order_card_secret_api_results r
INNER JOIN soft_close_success_orders s
        ON s.run_id = r.run_id
       AND s.order_no = r.order_no
WHERE r.run_id = :'run_id'
  AND s.payment_scenario IN ('CLOSING_USER_PAY_NEGATIVE', 'CLOSED_CALLBACK_NEGATIVE')
  AND (
      r.business_code = 'ORDER_CARD_SECRET_LIST_OK'
      OR COALESCE(r.delivered_count, 0) > 0
      OR COALESCE(r.secret_count, 0) > 0
      OR r.success IS DISTINCT FROM true
  )
ORDER BY s.success_index;

\echo order_soft_close_card_secret_api_db_delivery_mismatches
WITH db_deliveries AS (
    SELECT s.order_no,
           COUNT(d.id) AS delivered_count
    FROM soft_close_success_orders s
    LEFT JOIN order_card_secret_delivery d
           ON d.order_no = s.order_no
          AND d.status = 'DELIVERED'
    WHERE s.run_id = :'run_id'
    GROUP BY s.order_no
)
SELECT r.payment_scenario,
       r.order_no,
       r.delivered_count AS api_delivered_count,
       r.secret_count AS api_secret_count,
       COALESCE(d.delivered_count, 0) AS db_delivered_count
FROM order_card_secret_api_results r
LEFT JOIN db_deliveries d ON d.order_no = r.order_no
WHERE r.run_id = :'run_id'
  AND (
      COALESCE(r.delivered_count, 0) <> COALESCE(d.delivered_count, 0)
      OR COALESCE(r.secret_count, 0) <> COALESCE(d.delivered_count, 0)
  )
ORDER BY r.success_index;

\echo order_soft_close_card_secret_duplicate_delivery_failures_for_run
SELECT to_base62(d.card_secret_id) AS card_secret_id,
       COUNT(*) AS delivery_count,
       COUNT(DISTINCT d.order_no) AS order_count
FROM order_card_secret_delivery d
INNER JOIN soft_close_success_orders s ON s.order_no = d.order_no
WHERE s.run_id = :'run_id'
GROUP BY d.card_secret_id
HAVING COUNT(*) > 1 OR COUNT(DISTINCT d.order_no) > 1
ORDER BY delivery_count DESC, card_secret_id;

\echo order_soft_close_card_secret_duplicate_delivery_failures
SELECT to_base62(card_secret_id) AS card_secret_id,
       COUNT(*) AS delivery_count,
       COUNT(DISTINCT order_no) AS order_count
FROM order_card_secret_delivery
GROUP BY card_secret_id
HAVING COUNT(*) > 1 OR COUNT(DISTINCT order_no) > 1
ORDER BY delivery_count DESC, card_secret_id;

\echo order_soft_close_card_secret_sold_delivery_inconsistencies
SELECT to_base62(i.id) AS card_secret_id,
       i.status AS inventory_status,
       i.order_no AS inventory_order_no,
       d.order_no AS delivery_order_no,
       d.status AS delivery_status
FROM card_secret_inventory i
LEFT JOIN order_card_secret_delivery d ON d.card_secret_id = i.id
WHERE i.status = 'SOLD'
  AND (
      d.id IS NULL
      OR d.status <> 'DELIVERED'
      OR COALESCE(i.order_no, '') <> COALESCE(d.order_no, '')
  )
ORDER BY i.sold_at DESC, card_secret_id;

\echo order_soft_close_closed_callback_card_secret_refund_failures
SELECT s.order_no,
       o.status AS order_status,
       COUNT(d.id) AS delivered_count,
       COUNT(pr.id) AS refund_count
FROM soft_close_success_orders s
INNER JOIN trade_order o ON o.order_no = s.order_no
LEFT JOIN order_card_secret_delivery d ON d.order_no = s.order_no
LEFT JOIN payment_callback_inbox c
       ON c.order_no = s.order_no
      AND c.external_trade_no = ('JMETER-CLOSED-CALLBACK-' || :'run_id' || '-' || s.order_no)
LEFT JOIN payment_refund_record pr ON pr.refund_no = c.refund_no
WHERE s.run_id = :'run_id'
  AND s.payment_scenario = 'CLOSED_CALLBACK_NEGATIVE'
GROUP BY s.order_no, o.status
HAVING o.status <> 'CLOSED'
    OR COUNT(d.id) > 0
    OR COUNT(pr.id) = 0
ORDER BY s.order_no;

\echo order_soft_close_card_secret_delivery_summary
WITH delivered_by_order AS (
    SELECT order_no,
           COUNT(*) AS delivered_count
    FROM order_card_secret_delivery
    WHERE status = 'DELIVERED'
    GROUP BY order_no
)
SELECT s.payment_scenario,
       o.status AS order_status,
       COUNT(DISTINCT s.order_no) AS order_count,
       COALESCE(SUM(s.quantity), 0) AS ordered_quantity,
       COALESCE(SUM(d.delivered_count), 0) AS delivered_count
FROM soft_close_success_orders s
LEFT JOIN trade_order o ON o.order_no = s.order_no
LEFT JOIN delivered_by_order d ON d.order_no = s.order_no
WHERE s.run_id = :'run_id'
GROUP BY s.payment_scenario, o.status
ORDER BY MIN(s.success_index), o.status;
