-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" `
--     -v run_id=<run-id> `
--     -f loadtest/sql/verify-order-create.sql

\echo order_create_run_summary
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
)
SELECT COUNT(*) AS order_count,
       COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT') AS pending_payment_count,
       COUNT(*) FILTER (WHERE status = 'PAID') AS paid_count,
       COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
       COUNT(*) FILTER (WHERE status = 'CLOSED') AS closed_count,
       MIN(created_at) AS first_created_at,
       MAX(created_at) AS last_created_at
FROM run_orders;

\echo order_create_status_counts
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
)
SELECT status, COUNT(*) AS status_count
FROM run_orders
GROUP BY status
ORDER BY status;

\echo order_create_id_created_monotonic
WITH run_orders AS (
    SELECT id,
           order_no,
           created_at,
           LAG(created_at) OVER (ORDER BY id ASC) AS previous_created_at
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
)
SELECT COUNT(*) FILTER (
           WHERE previous_created_at IS NOT NULL
             AND created_at < previous_created_at
       ) AS created_at_regression_count,
       MIN(created_at) AS first_created_at,
       MAX(created_at) AS last_created_at
FROM run_orders;

\echo order_create_id_created_sample
WITH run_orders AS (
    SELECT id,
           order_no,
           created_at,
           expire_at
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
)
SELECT id,
       order_no,
       created_at,
       expire_at
FROM run_orders
ORDER BY id ASC
LIMIT 50;

\echo order_create_five_second_bucket
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
),
bucketed AS (
    SELECT to_timestamp(floor(extract(epoch FROM created_at) / 5) * 5) AS created_at_5s_bucket,
           COUNT(*) AS order_count,
           MIN(id) AS min_id,
           MAX(id) AS max_id,
           MIN(created_at) AS first_created_at,
           MAX(created_at) AS last_created_at
    FROM run_orders
    GROUP BY to_timestamp(floor(extract(epoch FROM created_at) / 5) * 5)
)
SELECT *
FROM bucketed
ORDER BY created_at_5s_bucket ASC;

\echo order_create_item_counts
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
)
SELECT COUNT(*) AS item_count,
       COALESCE(SUM(i.quantity), 0) AS total_quantity
FROM run_orders o
INNER JOIN trade_order_item i ON i.order_no = o.order_no;

\echo order_create_sku_safety
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
)
SELECT to_base62(i.sku_id) AS sku_id_base62,
       COUNT(*) AS order_count,
       SUM(i.quantity) AS ordered_quantity,
       h.stock_quantity AS configured_hot_stock,
       CASE
           WHEN h.stock_quantity IS NULL THEN 'HOT_SKU_NOT_FOUND'
           WHEN SUM(i.quantity) <= h.stock_quantity THEN 'OK'
           ELSE 'OVERSELL'
       END AS safety_result
FROM run_orders o
INNER JOIN trade_order_item i ON i.order_no = o.order_no
LEFT JOIN product_hot_sku h ON h.sku_id = i.sku_id
GROUP BY i.sku_id, h.stock_quantity
ORDER BY ordered_quantity DESC, sku_id_base62;

\echo order_create_duplicate_user_sku
WITH run_orders AS (
    SELECT *
    FROM trade_order
    WHERE idempotency_key LIKE ('%order-loadtest-' || :'run_id' || '-%')
)
SELECT o.user_id,
       to_base62(i.sku_id) AS sku_id_base62,
       COUNT(*) AS duplicate_count
FROM run_orders o
INNER JOIN trade_order_item i ON i.order_no = o.order_no
GROUP BY o.user_id, i.sku_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, o.user_id, sku_id_base62;
