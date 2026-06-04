-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" `
--     -v run_id=<run-id> `
--     -v callback_input_csv=<loadtest-output/order-callback-batch-input.csv> `
--     -v callback_result_csv=<loadtest-output/runs/.../order-callback-batch-results.csv> `
--     -f loadtest/sql/verify-order-callback-batch-refund.sql

CREATE TEMP TABLE callback_batch_input (
    order_no text,
    external_trade_no text,
    paid_amount_yuan numeric,
    payment_provider text,
    delay_ms bigint,
    expected_outcome text
);

CREATE TEMP TABLE callback_batch_result (
    run_id text,
    order_no text,
    external_trade_no text,
    paid_amount_yuan numeric,
    payment_provider text,
    expected_outcome text,
    callback_no text,
    accepted_status text,
    http_code integer,
    callback_started_at_ms bigint
);

\copy callback_batch_input FROM :'callback_input_csv' CSV HEADER
\copy callback_batch_result FROM :'callback_result_csv' CSV HEADER

\echo order_callback_batch_http_acceptance
SELECT COUNT(*) AS callback_count,
       COUNT(*) FILTER (WHERE http_code = 200 AND accepted_status = 'RECEIVED') AS received_count,
       COUNT(*) FILTER (WHERE http_code <> 200 OR accepted_status <> 'RECEIVED') AS failed_accept_count
FROM callback_batch_result
WHERE run_id = :'run_id';

\echo order_callback_batch_inbox_status_counts
SELECT i.status,
       i.result_outcome,
       COUNT(*) AS callback_count
FROM callback_batch_result r
INNER JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
WHERE r.run_id = :'run_id'
GROUP BY i.status, i.result_outcome
ORDER BY i.status, i.result_outcome;

\echo order_callback_batch_expected_outcome_failures
SELECT r.order_no,
       r.external_trade_no,
       r.expected_outcome,
       i.status AS inbox_status,
       i.result_outcome,
       i.result_order_status,
       i.refund_no,
       i.last_error_code
FROM callback_batch_result r
LEFT JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome IS NOT NULL
  AND btrim(r.expected_outcome) <> ''
  AND COALESCE(i.result_outcome, '') <> r.expected_outcome
ORDER BY r.order_no, r.external_trade_no;

\echo order_callback_batch_order_status_after_callbacks
SELECT r.expected_outcome,
       o.status AS order_status,
       COUNT(*) AS order_count
FROM callback_batch_result r
LEFT JOIN trade_order o ON o.order_no = r.order_no
WHERE r.run_id = :'run_id'
GROUP BY r.expected_outcome, o.status
ORDER BY r.expected_outcome, o.status;

\echo order_callback_batch_refund_records
SELECT pr.status AS refund_status,
       pr.reason_code,
       COUNT(*) AS refund_count,
       COALESCE(SUM(pr.refund_amount_yuan), 0) AS refund_amount_yuan
FROM callback_batch_result r
INNER JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
INNER JOIN payment_refund_record pr ON pr.refund_no = i.refund_no
WHERE r.run_id = :'run_id'
GROUP BY pr.status, pr.reason_code
ORDER BY pr.status, pr.reason_code;

\echo order_callback_batch_duplicate_inbox_check
SELECT order_no,
       external_trade_no,
       COUNT(*) AS inbox_count
FROM payment_callback_inbox
WHERE (order_no, external_trade_no) IN (
    SELECT order_no, external_trade_no
    FROM callback_batch_result
    WHERE run_id = :'run_id'
)
GROUP BY order_no, external_trade_no
HAVING COUNT(*) > 1
ORDER BY inbox_count DESC, order_no, external_trade_no;

\echo order_callback_batch_duplicate_refund_check
SELECT pr.order_no,
       pr.external_trade_no,
       COUNT(*) AS refund_count
FROM payment_refund_record pr
WHERE (pr.order_no, pr.external_trade_no) IN (
    SELECT order_no, external_trade_no
    FROM callback_batch_result
    WHERE run_id = :'run_id'
)
GROUP BY pr.order_no, pr.external_trade_no
HAVING COUNT(*) > 1
ORDER BY refund_count DESC, pr.order_no, pr.external_trade_no;

\echo order_callback_batch_refund_outcome_card_secret_delivery_failures
SELECT r.order_no,
       r.external_trade_no,
       r.expected_outcome,
       o.status AS order_status,
       COUNT(d.id) AS delivered_count,
       COUNT(pr.id) AS refund_count
FROM callback_batch_result r
LEFT JOIN trade_order o ON o.order_no = r.order_no
LEFT JOIN order_card_secret_delivery d ON d.order_no = r.order_no
LEFT JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
LEFT JOIN payment_refund_record pr ON pr.refund_no = i.refund_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome = 'REFUND_PENDING'
GROUP BY r.order_no, r.external_trade_no, r.expected_outcome, o.status
HAVING COUNT(d.id) > 0
    OR COUNT(pr.id) = 0
ORDER BY r.order_no, r.external_trade_no;

\echo order_callback_batch_paid_idempotent_card_secret_over_delivery_failures
WITH paid_targets AS (
    SELECT DISTINCT r.order_no
    FROM callback_batch_result r
    WHERE r.run_id = :'run_id'
      AND r.expected_outcome IN ('PAID', 'PAID_IDEMPOTENT')
),
order_quantities AS (
    SELECT p.order_no,
           COALESCE(SUM(i.quantity), 0) AS expected_quantity
    FROM paid_targets p
    LEFT JOIN trade_order_item i ON i.order_no = p.order_no
    GROUP BY p.order_no
),
deliveries AS (
    SELECT p.order_no,
           COUNT(d.id) AS delivered_count
    FROM paid_targets p
    LEFT JOIN order_card_secret_delivery d
           ON d.order_no = p.order_no
          AND d.status = 'DELIVERED'
    GROUP BY p.order_no
)
SELECT q.order_no,
       q.expected_quantity,
       d.delivered_count
FROM order_quantities q
INNER JOIN deliveries d ON d.order_no = q.order_no
WHERE d.delivered_count > q.expected_quantity
ORDER BY q.order_no;

\echo order_callback_batch_card_secret_duplicate_delivery_failures
SELECT to_base62(card_secret_id) AS card_secret_id,
       COUNT(*) AS delivery_count,
       COUNT(DISTINCT order_no) AS order_count
FROM order_card_secret_delivery
GROUP BY card_secret_id
HAVING COUNT(*) > 1 OR COUNT(DISTINCT order_no) > 1
ORDER BY delivery_count DESC, card_secret_id;
