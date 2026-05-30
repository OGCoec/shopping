package com.example.ShoppingSystem.tools.loadtest;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CouponLoadtestUserSeedMain {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/shopping";
    private static final String DEFAULT_DB_USERNAME = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "123456";
    private static final long DEFAULT_START_USER_ID = 1L;
    private static final long DEFAULT_END_USER_ID = 500L;

    public static void main(String[] args) throws Exception {
        long startUserId = parsePositiveLong(args, 0, DEFAULT_START_USER_ID, "startUserId");
        long endUserId = parsePositiveLong(args, 1, DEFAULT_END_USER_ID, "endUserId");
        if (startUserId > endUserId) {
            throw new IllegalArgumentException("startUserId must be less than or equal to endUserId.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(envOrDefault("SHOPPING_LOADTEST_DB_URL", DEFAULT_DB_URL));
        config.setUsername(envOrDefault("SHOPPING_LOADTEST_DB_USERNAME", DEFAULT_DB_USERNAME));
        config.setPassword(envOrDefault("SHOPPING_LOADTEST_DB_PASSWORD", DEFAULT_DB_PASSWORD));
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(2);
        config.setPoolName("coupon-loadtest-user-seed");

        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int identityRows = seedUserLoginIdentity(connection, startUserId, endUserId);
                int profileRows = seedUserProfile(connection, startUserId, endUserId);
                connection.commit();
                System.out.printf("Seeded loadtest user_login_identity rows: %d%n", identityRows);
                System.out.printf("Seeded loadtest user_profile rows: %d%n", profileRows);
                System.out.printf("User id range: %d-%d%n", startUserId, endUserId);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static int seedUserLoginIdentity(Connection connection, long startUserId, long endUserId) throws SQLException {
        String sql = """
                INSERT INTO user_login_identity (
                    id,
                    user_id,
                    email,
                    email_verified,
                    phone,
                    phone_verified,
                    token_version,
                    totp_enabled,
                    status,
                    last_login_at,
                    created_at,
                    updated_at
                )
                SELECT
                    n,
                    n,
                    'loadtest_user_' || lpad(n::text, 4, '0') || '@local.test',
                    true,
                    '+1999000' || lpad(n::text, 4, '0'),
                    true,
                    substring(md5('coupon-loadtest-' || n || '-' || clock_timestamp()::text) from 1 for 24),
                    false,
                    'ACTIVE',
                    NOW(),
                    NOW(),
                    NOW()
                FROM generate_series(?, ?) AS n
                ON CONFLICT (id) DO UPDATE
                SET user_id = EXCLUDED.user_id,
                    email = EXCLUDED.email,
                    email_verified = EXCLUDED.email_verified,
                    phone = EXCLUDED.phone,
                    phone_verified = EXCLUDED.phone_verified,
                    token_version = EXCLUDED.token_version,
                    totp_enabled = EXCLUDED.totp_enabled,
                    status = EXCLUDED.status,
                    last_login_at = EXCLUDED.last_login_at,
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, startUserId);
            statement.setLong(2, endUserId);
            return statement.executeUpdate();
        }
    }

    private static int seedUserProfile(Connection connection, long startUserId, long endUserId) throws SQLException {
        String sql = """
                INSERT INTO user_profile (
                    id,
                    first_name,
                    last_name,
                    username,
                    gender,
                    bio,
                    country,
                    language,
                    timezone,
                    created_at,
                    updated_at
                )
                SELECT
                    n,
                    'Loadtest',
                    lpad(n::text, 4, '0'),
                    'loadtest_user_' || lpad(n::text, 4, '0'),
                    'UNKNOWN',
                    'coupon claim loadtest user',
                    'CN',
                    'zh-CN',
                    'Asia/Shanghai',
                    NOW(),
                    NOW()
                FROM generate_series(?, ?) AS n
                ON CONFLICT (id) DO UPDATE
                SET first_name = EXCLUDED.first_name,
                    last_name = EXCLUDED.last_name,
                    username = EXCLUDED.username,
                    gender = EXCLUDED.gender,
                    bio = EXCLUDED.bio,
                    country = EXCLUDED.country,
                    language = EXCLUDED.language,
                    timezone = EXCLUDED.timezone,
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, startUserId);
            statement.setLong(2, endUserId);
            return statement.executeUpdate();
        }
    }

    private static long parsePositiveLong(String[] args, int index, long defaultValue, String name) {
        String value = arg(args, index, String.valueOf(defaultValue));
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(name + " must be a positive long: " + value);
    }

    private static String arg(String[] args, int index, String defaultValue) {
        if (args.length <= index || args[index] == null || args[index].isBlank()) {
            return defaultValue;
        }
        return args[index].trim();
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
