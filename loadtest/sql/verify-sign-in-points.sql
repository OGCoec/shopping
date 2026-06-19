\set ON_ERROR_STOP on

\if :{?continuous_user_id}
\else
\set continuous_user_id 2
\endif

\if :{?reset_user_id}
\else
\set reset_user_id 3
\endif

\if :{?concurrent_user_id}
\else
\set concurrent_user_id 1
\endif

CREATE TEMP TABLE sign_in_verify_params AS
SELECT (:continuous_user_id)::bigint AS continuous_user_id,
       (:reset_user_id)::bigint AS reset_user_id,
       (:concurrent_user_id)::bigint AS concurrent_user_id;

DO $$
DECLARE
    continuous_user_id bigint;
    reset_user_id bigint;
    concurrent_user_id bigint;
    record_count integer;
    available_points bigint;
    total_earned_points bigint;
    concurrent_reward_points integer;
BEGIN
    SELECT p.continuous_user_id,
           p.reset_user_id,
           p.concurrent_user_id
    INTO continuous_user_id,
         reset_user_id,
         concurrent_user_id
    FROM sign_in_verify_params p;

    SELECT COUNT(*)::integer
    INTO record_count
    FROM user_sign_record
    WHERE user_id = continuous_user_id;
    IF record_count <> 33 THEN
        RAISE EXCEPTION 'continuous user % expected 33 sign rows, got %', continuous_user_id, record_count;
    END IF;

    IF NOT EXISTS (
        WITH ordered AS (
            SELECT row_number() OVER (ORDER BY id) AS rn,
                   reward_points,
                   continuous_count,
                   cycle_day
            FROM user_sign_record
            WHERE user_id = continuous_user_id
        )
        SELECT 1 FROM ordered
        WHERE rn = 3
          AND reward_points = 3
          AND continuous_count = 3
          AND cycle_day = 3
    ) THEN
        RAISE EXCEPTION 'continuous user % failed 3-period milestone check', continuous_user_id;
    END IF;

    IF NOT EXISTS (
        WITH ordered AS (
            SELECT row_number() OVER (ORDER BY id) AS rn,
                   reward_points,
                   continuous_count,
                   cycle_day
            FROM user_sign_record
            WHERE user_id = continuous_user_id
        )
        SELECT 1 FROM ordered
        WHERE rn = 7
          AND reward_points = 10
          AND continuous_count = 7
          AND cycle_day = 7
    ) THEN
        RAISE EXCEPTION 'continuous user % failed 7-period milestone check', continuous_user_id;
    END IF;

    IF NOT EXISTS (
        WITH ordered AS (
            SELECT row_number() OVER (ORDER BY id) AS rn,
                   reward_points,
                   continuous_count,
                   cycle_day
            FROM user_sign_record
            WHERE user_id = continuous_user_id
        )
        SELECT 1 FROM ordered
        WHERE rn = 30
          AND reward_points = 50
          AND continuous_count = 30
          AND cycle_day = 30
    ) THEN
        RAISE EXCEPTION 'continuous user % failed 30-period milestone check', continuous_user_id;
    END IF;

    IF NOT EXISTS (
        WITH ordered AS (
            SELECT row_number() OVER (ORDER BY id) AS rn,
                   reward_points,
                   continuous_count,
                   cycle_day
            FROM user_sign_record
            WHERE user_id = continuous_user_id
        )
        SELECT 1 FROM ordered
        WHERE rn = 33
          AND reward_points = 3
          AND continuous_count = 33
          AND cycle_day = 3
    ) THEN
        RAISE EXCEPTION 'continuous user % failed next-cycle 33-period milestone check', continuous_user_id;
    END IF;

    SELECT available_points,
           total_earned_points
    INTO available_points,
         total_earned_points
    FROM user_point_account
    WHERE user_id = continuous_user_id;
    IF available_points <> 95 OR total_earned_points <> 95 THEN
        RAISE EXCEPTION 'continuous user % expected points 95/95, got %/%',
            continuous_user_id, available_points, total_earned_points;
    END IF;

    SELECT COUNT(*)::integer
    INTO record_count
    FROM user_sign_record
    WHERE user_id = reset_user_id;
    IF record_count <> 2 THEN
        RAISE EXCEPTION 'reset user % expected 2 sign rows, got %', reset_user_id, record_count;
    END IF;

    IF NOT EXISTS (
        WITH ordered AS (
            SELECT row_number() OVER (ORDER BY id) AS rn,
                   reward_points,
                   continuous_count,
                   cycle_day
            FROM user_sign_record
            WHERE user_id = reset_user_id
        )
        SELECT 1 FROM ordered
        WHERE rn = 2
          AND reward_points = 1
          AND continuous_count = 1
          AND cycle_day = 1
    ) THEN
        RAISE EXCEPTION 'reset user % did not reset to continuous_count=1 on second row', reset_user_id;
    END IF;

    SELECT available_points,
           total_earned_points
    INTO available_points,
         total_earned_points
    FROM user_point_account
    WHERE user_id = reset_user_id;
    IF available_points <> 2 OR total_earned_points <> 2 THEN
        RAISE EXCEPTION 'reset user % expected points 2/2, got %/%',
            reset_user_id, available_points, total_earned_points;
    END IF;

    SELECT COUNT(*)::integer
    INTO record_count
    FROM user_sign_record
    WHERE user_id = concurrent_user_id;
    IF record_count <> 1 THEN
        RAISE EXCEPTION 'concurrent user % expected exactly 1 sign row, got %', concurrent_user_id, record_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_sign_record
        WHERE user_id = concurrent_user_id
        GROUP BY sign_period_key
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'concurrent user % has duplicate sign_period_key rows', concurrent_user_id;
    END IF;

    SELECT reward_points
    INTO concurrent_reward_points
    FROM user_sign_record
    WHERE user_id = concurrent_user_id
    ORDER BY id
    LIMIT 1;

    SELECT available_points,
           total_earned_points
    INTO available_points,
         total_earned_points
    FROM user_point_account
    WHERE user_id = concurrent_user_id;
    IF available_points <> concurrent_reward_points OR total_earned_points <> concurrent_reward_points THEN
        RAISE EXCEPTION 'concurrent user % expected points equal one reward %, got %/%',
            concurrent_user_id, concurrent_reward_points, available_points, total_earned_points;
    END IF;
END
$$;

SELECT 'continuous_rows' AS check_name,
       COUNT(*) AS value
FROM user_sign_record
WHERE user_id = (:continuous_user_id)::bigint
UNION ALL
SELECT 'reset_rows' AS check_name,
       COUNT(*) AS value
FROM user_sign_record
WHERE user_id = (:reset_user_id)::bigint
UNION ALL
SELECT 'concurrent_rows' AS check_name,
       COUNT(*) AS value
FROM user_sign_record
WHERE user_id = (:concurrent_user_id)::bigint;

WITH selected_users AS (
    SELECT (:continuous_user_id)::bigint AS user_id
    UNION ALL
    SELECT (:reset_user_id)::bigint
    UNION ALL
    SELECT (:concurrent_user_id)::bigint
)
SELECT a.user_id,
       a.available_points,
       a.total_earned_points,
       a.total_used_points,
       a.version
FROM user_point_account a
INNER JOIN selected_users u ON u.user_id = a.user_id
ORDER BY a.user_id;

WITH selected_users AS (
    SELECT (:continuous_user_id)::bigint AS user_id
    UNION ALL
    SELECT (:reset_user_id)::bigint
    UNION ALL
    SELECT (:concurrent_user_id)::bigint
)
SELECT r.user_id,
       r.sign_period_key,
       COUNT(*) AS row_count
FROM user_sign_record r
INNER JOIN selected_users u ON u.user_id = r.user_id
GROUP BY r.user_id, r.sign_period_key
ORDER BY r.user_id, r.sign_period_key;
