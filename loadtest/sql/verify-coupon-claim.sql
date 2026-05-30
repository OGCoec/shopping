-- Usage:
--   psql "postgresql://postgres:123456@127.0.0.1:5432/shopping" `
--     -v coupon_template_id_hex=<coupon-template-id-hex> `
--     -f loadtest/sql/verify-coupon-claim.sql

SELECT COUNT(*) AS claimed_count
FROM user_coupon
WHERE coupon_template_id = decode(:'coupon_template_id_hex', 'hex');

SELECT status, COUNT(*) AS status_count
FROM user_coupon
WHERE coupon_template_id = decode(:'coupon_template_id_hex', 'hex')
GROUP BY status
ORDER BY status;

SELECT user_id,
       encode(coupon_template_id, 'hex') AS coupon_template_id_hex,
       COUNT(*) AS duplicate_count
FROM user_coupon
WHERE coupon_template_id = decode(:'coupon_template_id_hex', 'hex')
GROUP BY user_id, coupon_template_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, user_id;
