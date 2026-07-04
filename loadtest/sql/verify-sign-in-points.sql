\set ON_ERROR_STOP on

\if :{?repeat_user_id}
\else
\set repeat_user_id 2
\endif

\if :{?other_user_id}
\else
\set other_user_id 3
\endif

\if :{?concurrent_user_id}
\else
\set concurrent_user_id 1
\endif

CREATE TEMP TABLE sign_in_verify_params AS
SELECT (:repeat_user_id)::bigint AS repeat_user_id,
       (:other_user_id)::bigint AS other_user_id,
       (:concurrent_user_id)::bigint AS concurrent_user_id;

DO $$
DECLARE
    repeat_user_id bigint;
    other_user_id bigint;
    concurrent_user_id bigint;
    record_count integer;
    selected_sign_date_count integer;
    reward_points integer;
    continuous_count integer;
    cycle_day integer;
    available_points bigint;
    total_earned_points bigint;
    concurrent_reward_points integer;
BEGIN
    SELECT p.repeat_user_id,
           p.other_user_id,
           p.concurrent_user_id
    INTO repeat_user_id,
         other_user_id,
         concurrent_user_id
    FROM sign_in_verify_params p;

    SELECT COUNT(*)::integer
    INTO record_count
    FROM user_sign_record
    WHERE user_id = repeat_user_id;
    IF record_count <> 1 THEN
        RAISE EXCEPTION 'repeat user % expected exactly 1 same-day sign row, got %',
            repeat_user_id, record_count;
    END IF;

    SELECT reward_points,
           continuous_count,
           cycle_day
    INTO reward_points,
         continuous_count,
         cycle_day
    FROM user_sign_record
    WHERE user_id = repeat_user_id
    ORDER BY sign_date, id
    LIMIT 1;
    IF reward_points <> 1 OR continuous_count <> 1 OR cycle_day <> 1 THEN
        RAISE EXCEPTION 'repeat user % expected first-day reward/count/cycle 1/1/1, got %/%/%',
            repeat_user_id, reward_points, continuous_count, cycle_day;
    END IF;

    SELECT available_points,
           total_earned_points
    INTO available_points,
         total_earned_points
    FROM user_point_account
    WHERE user_id = repeat_user_id;
    IF available_points <> 1 OR total_earned_points <> 1 THEN
        RAISE EXCEPTION 'repeat user % expected points 1/1 after duplicate clicks, got %/%',
            repeat_user_id, available_points, total_earned_points;
    END IF;

    SELECT COUNT(*)::integer
    INTO record_count
    FROM user_sign_record
    WHERE user_id = other_user_id;
    IF record_count <> 1 THEN
        RAISE EXCEPTION 'other user % expected exactly 1 same-day sign row, got %',
            other_user_id, record_count;
    END IF;

    SELECT available_points,
           total_earned_points
    INTO available_points,
         total_earned_points
    FROM user_point_account
    WHERE user_id = other_user_id;
    IF available_points <> 1 OR total_earned_points <> 1 THEN
        RAISE EXCEPTION 'other user % expected points 1/1, got %/%',
            other_user_id, available_points, total_earned_points;
    END IF;

    SELECT COUNT(*)::integer
    INTO record_count
    FROM user_sign_record
    WHERE user_id = concurrent_user_id;
    IF record_count <> 1 THEN
        RAISE EXCEPTION 'concurrent user % expected exactly 1 same-day sign row, got %',
            concurrent_user_id, record_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_sign_record
        WHERE user_id IN (repeat_user_id, other_user_id, concurrent_user_id)
        GROUP BY user_id, sign_date
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'selected sign-in users have duplicate user_id + sign_date rows';
    END IF;

    SELECT COUNT(DISTINCT sign_date)::integer
    INTO selected_sign_date_count
    FROM user_sign_record
    WHERE user_id IN (repeat_user_id, other_user_id, concurrent_user_id);
    IF selected_sign_date_count <> 1 THEN
        RAISE EXCEPTION 'selected sign-in users expected one shared sign_date, got % distinct dates',
            selected_sign_date_count;
    END IF;

    SELECT reward_points
    INTO concurrent_reward_points
    FROM user_sign_record
    WHERE user_id = concurrent_user_id
    ORDER BY sign_date, id
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

SELECT 'repeat_rows' AS check_name,
       COUNT(*) AS value
FROM user_sign_record
WHERE user_id = (:repeat_user_id)::bigint
UNION ALL
SELECT 'other_rows' AS check_name,
       COUNT(*) AS value
FROM user_sign_record
WHERE user_id = (:other_user_id)::bigint
UNION ALL
SELECT 'concurrent_rows' AS check_name,
       COUNT(*) AS value
FROM user_sign_record
WHERE user_id = (:concurrent_user_id)::bigint;

WITH selected_users AS (
    SELECT (:repeat_user_id)::bigint AS user_id
    UNION ALL
    SELECT (:other_user_id)::bigint
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
    SELECT (:repeat_user_id)::bigint AS user_id
    UNION ALL
    SELECT (:other_user_id)::bigint
    UNION ALL
    SELECT (:concurrent_user_id)::bigint
)
SELECT r.user_id,
       r.sign_date,
       COUNT(*) AS row_count
FROM user_sign_record r
INNER JOIN selected_users u ON u.user_id = r.user_id
GROUP BY r.user_id, r.sign_date
ORDER BY r.user_id, r.sign_date;
