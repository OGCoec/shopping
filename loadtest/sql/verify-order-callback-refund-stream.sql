-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" `
--     -v run_id=<run-id> `
--     -v callback_input_csv=<loadtest-output/order-callback-batch-input.csv> `
--     -v callback_result_csv=<loadtest-output/runs/.../order-callback-refund-stream-results.csv> `
--     -f loadtest/sql/verify-order-callback-refund-stream.sql

CREATE TEMP TABLE callback_stream_input (
    order_no text,
    external_trade_no text,
    paid_amount_yuan numeric,
    payment_provider text,
    delay_ms bigint,
    expected_outcome text
);

CREATE TEMP TABLE callback_stream_result (
    run_id text,
    order_no text,
    external_trade_no text,
    paid_amount_yuan text,
    payment_provider text,
    expected_outcome text,
    callback_no text,
    accepted_status text,
    http_code text,
    callback_started_at_ms text
);

CREATE TEMP TABLE callback_stream_failures (
    failure_type text,
    order_no text,
    external_trade_no text,
    detail text
);

\copy callback_stream_input FROM :'callback_input_csv' CSV HEADER
\copy callback_stream_result FROM :'callback_result_csv' CSV HEADER

DELETE FROM callback_stream_result
WHERE run_id IS NULL
   OR run_id = ''
   OR run_id = 'run_id';

\echo callback_refund_stream_http_acceptance
SELECT COUNT(*) AS callback_count,
       COUNT(*) FILTER (WHERE http_code = '200' AND accepted_status = 'RECEIVED') AS received_count,
       COUNT(*) FILTER (WHERE http_code <> '200' OR accepted_status <> 'RECEIVED') AS failed_accept_count
FROM callback_stream_result
WHERE run_id = :'run_id';

\echo callback_refund_stream_inbox_status_counts
SELECT i.status,
       i.result_outcome,
       COUNT(*) AS callback_count
FROM callback_stream_result r
LEFT JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
WHERE r.run_id = :'run_id'
GROUP BY i.status, i.result_outcome
ORDER BY i.status, i.result_outcome;

\echo callback_refund_stream_order_status_counts
SELECT r.expected_outcome,
       COALESCE(o.status, 'NOT_FOUND') AS order_status,
       COUNT(*) AS order_count
FROM callback_stream_result r
LEFT JOIN trade_order o ON o.order_no = r.order_no
WHERE r.run_id = :'run_id'
GROUP BY r.expected_outcome, COALESCE(o.status, 'NOT_FOUND')
ORDER BY r.expected_outcome, COALESCE(o.status, 'NOT_FOUND');

\echo callback_refund_stream_refund_status_counts
SELECT pr.status,
       pr.reason_code,
       pr.payment_provider,
       COUNT(*) AS refund_count,
       COALESCE(SUM(pr.refund_amount_yuan), 0) AS refund_amount_yuan
FROM callback_stream_result r
LEFT JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
LEFT JOIN payment_refund_record pr ON pr.refund_no = i.refund_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome = 'REFUND_PENDING'
GROUP BY pr.status, pr.reason_code, pr.payment_provider
ORDER BY pr.status, pr.reason_code, pr.payment_provider;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'callback_result_missing',
       input.order_no,
       input.external_trade_no,
       'input row was not recorded in callback result csv'
FROM callback_stream_input input
LEFT JOIN callback_stream_result result
       ON result.run_id = :'run_id'
      AND result.order_no = input.order_no
      AND result.external_trade_no = input.external_trade_no
WHERE result.order_no IS NULL;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'callback_result_unexpected',
       result.order_no,
       result.external_trade_no,
       'result row was not found in callback input csv'
FROM callback_stream_result result
LEFT JOIN callback_stream_input input
       ON input.order_no = result.order_no
      AND input.external_trade_no = result.external_trade_no
WHERE result.run_id = :'run_id'
  AND input.order_no IS NULL;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'callback_not_accepted',
       order_no,
       external_trade_no,
       'http_code=' || COALESCE(http_code::text, 'NULL')
           || ', accepted_status=' || COALESCE(accepted_status, 'NULL')
FROM callback_stream_result
WHERE run_id = :'run_id'
  AND (http_code <> '200' OR accepted_status <> 'RECEIVED');

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'callback_no_missing',
       order_no,
       external_trade_no,
       'callback_no is blank after accepted callback'
FROM callback_stream_result
WHERE run_id = :'run_id'
  AND http_code = '200'
  AND accepted_status = 'RECEIVED'
  AND NULLIF(callback_no, '') IS NULL;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'inbox_missing_after_stream_flush',
       r.order_no,
       r.external_trade_no,
       'callback_no=' || COALESCE(r.callback_no, 'NULL')
FROM callback_stream_result r
LEFT JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
WHERE r.run_id = :'run_id'
  AND i.callback_no IS NULL;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'inbox_not_processed',
       r.order_no,
       r.external_trade_no,
       'callback_no=' || COALESCE(r.callback_no, 'NULL')
           || ', status=' || COALESCE(i.status, 'NULL')
           || ', last_error_code=' || COALESCE(i.last_error_code, 'NULL')
FROM callback_stream_result r
INNER JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
WHERE r.run_id = :'run_id'
  AND i.status <> 'PROCESSED';

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'callback_outcome_mismatch',
       r.order_no,
       r.external_trade_no,
       'expected=' || COALESCE(r.expected_outcome, 'NULL')
           || ', actual=' || COALESCE(i.result_outcome, 'NULL')
           || ', order_status=' || COALESCE(i.result_order_status, 'NULL')
           || ', refund_no=' || COALESCE(i.refund_no, 'NULL')
FROM callback_stream_result r
LEFT JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
WHERE r.run_id = :'run_id'
  AND NULLIF(r.expected_outcome, '') IS NOT NULL
  AND COALESCE(i.result_outcome, '') <> r.expected_outcome;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'paid_order_not_paid',
       r.order_no,
       r.external_trade_no,
       'expected_outcome=' || COALESCE(r.expected_outcome, 'NULL')
           || ', order_status=' || COALESCE(o.status, 'NOT_FOUND')
FROM callback_stream_result r
LEFT JOIN trade_order o ON o.order_no = r.order_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome IN ('PAID', 'PAID_IDEMPOTENT')
  AND COALESCE(o.status, 'NOT_FOUND') <> 'PAID';

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'paid_callback_created_refund',
       r.order_no,
       r.external_trade_no,
       'refund_no=' || COALESCE(i.refund_no, 'NULL')
FROM callback_stream_result r
INNER JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome IN ('PAID', 'PAID_IDEMPOTENT')
  AND i.refund_no IS NOT NULL;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'refund_record_missing',
       r.order_no,
       r.external_trade_no,
       'callback_no=' || COALESCE(r.callback_no, 'NULL')
           || ', inbox_refund_no=' || COALESCE(i.refund_no, 'NULL')
FROM callback_stream_result r
LEFT JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
LEFT JOIN payment_refund_record pr ON pr.refund_no = i.refund_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome = 'REFUND_PENDING'
  AND pr.refund_no IS NULL;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'simulated_refund_not_refunded',
       r.order_no,
       r.external_trade_no,
       'refund_no=' || COALESCE(pr.refund_no, 'NULL')
           || ', refund_status=' || COALESCE(pr.status, 'NULL')
           || ', external_refund_no=' || COALESCE(pr.external_refund_no, 'NULL')
FROM callback_stream_result r
INNER JOIN payment_callback_inbox i ON i.callback_no = r.callback_no
INNER JOIN payment_refund_record pr ON pr.refund_no = i.refund_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome = 'REFUND_PENDING'
  AND upper(COALESCE(r.payment_provider, '')) = 'SIMULATED'
  AND (pr.status <> 'REFUNDED' OR pr.external_refund_no IS NULL);

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'duplicate_callback_inbox',
       order_no,
       external_trade_no,
       'inbox_count=' || COUNT(*)
FROM payment_callback_inbox
WHERE (order_no, external_trade_no) IN (
    SELECT order_no, external_trade_no
    FROM callback_stream_result
    WHERE run_id = :'run_id'
)
GROUP BY order_no, external_trade_no
HAVING COUNT(*) > 1;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'duplicate_refund_record',
       pr.order_no,
       pr.external_trade_no,
       'refund_count=' || COUNT(*)
FROM payment_refund_record pr
WHERE (pr.order_no, pr.external_trade_no) IN (
    SELECT order_no, external_trade_no
    FROM callback_stream_result
    WHERE run_id = :'run_id'
)
GROUP BY pr.order_no, pr.external_trade_no
HAVING COUNT(*) > 1;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
SELECT 'refund_callback_delivered_card_secret',
       r.order_no,
       r.external_trade_no,
       'delivered_count=' || COUNT(d.id)
FROM callback_stream_result r
LEFT JOIN order_card_secret_delivery d ON d.order_no = r.order_no
WHERE r.run_id = :'run_id'
  AND r.expected_outcome = 'REFUND_PENDING'
GROUP BY r.order_no, r.external_trade_no
HAVING COUNT(d.id) > 0;

INSERT INTO callback_stream_failures (failure_type, order_no, external_trade_no, detail)
WITH paid_targets AS (
    SELECT DISTINCT r.order_no
    FROM callback_stream_result r
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
SELECT 'paid_order_card_secret_over_delivery',
       q.order_no,
       NULL,
       'expected_quantity=' || q.expected_quantity || ', delivered_count=' || d.delivered_count
FROM order_quantities q
INNER JOIN deliveries d ON d.order_no = q.order_no
WHERE d.delivered_count > q.expected_quantity;

\echo callback_refund_stream_failure_details
SELECT *
FROM callback_stream_failures
ORDER BY failure_type, order_no, external_trade_no;

\echo callback_refund_stream_verification_result
SELECT COUNT(*) AS failure_count
FROM callback_stream_failures;

DO $$
DECLARE
    failure_count integer;
BEGIN
    SELECT COUNT(*) INTO failure_count
    FROM callback_stream_failures;

    IF failure_count > 0 THEN
        RAISE EXCEPTION 'callback refund stream verification failed: % failure rows', failure_count;
    END IF;
END $$;
