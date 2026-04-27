-- ============================================
-- 文件名：010_drop_shared_user_columns_from_ip_reputation_profiles.sql
-- 说明：移除 IP 信誉画像表中的共享人数相关字段
-- 适配：PostgreSQL
-- ============================================

ALTER TABLE IF EXISTS ipv4_reputation_profile
    DROP COLUMN IF EXISTS shared_users,
    DROP COLUMN IF EXISTS shared_users_tier;

ALTER TABLE IF EXISTS ipv6_reputation_profile
    DROP COLUMN IF EXISTS shared_users,
    DROP COLUMN IF EXISTS shared_users_tier;
