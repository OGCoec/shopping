package com.example.ShoppingSystem.tools.loadtest;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.coupon.service.CouponRedisKeys;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CouponLoadtestCouponSeedMain {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/shopping";
    private static final String DEFAULT_DB_USERNAME = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "123456";
    private static final String DEFAULT_REDIS_HOST = "127.0.0.1";
    private static final int DEFAULT_REDIS_PORT = 6380;
    private static final String DEFAULT_REDIS_PASSWORD = "123456";
    private static final int DEFAULT_REDIS_DATABASE = 1;
    private static final String SAME_USER_COUPON_HEX = "0000000000000000000000000000c101";
    private static final String DIFFERENT_USERS_COUPON_HEX = "0000000000000000000000000000c102";
    private static final int SAME_USER_STOCK = 500;
    private static final int DIFFERENT_USERS_STOCK = 100;

    public static void main(String[] args) throws Exception {
        CouponSeed sameUserCoupon = new CouponSeed(
                HybridIdCodec.fromHex(SAME_USER_COUPON_HEX),
                "LOADTEST-SAME-USER-001",
                "Loadtest Same User Coupon",
                SAME_USER_STOCK
        );
        CouponSeed differentUsersCoupon = new CouponSeed(
                HybridIdCodec.fromHex(DIFFERENT_USERS_COUPON_HEX),
                "LOADTEST-DIFFERENT-USERS-001",
                "Loadtest Different Users Coupon",
                DIFFERENT_USERS_STOCK
        );
        List<CouponSeed> coupons = List.of(sameUserCoupon, differentUsersCoupon);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(envOrDefault("SHOPPING_LOADTEST_DB_URL", DEFAULT_DB_URL));
        config.setUsername(envOrDefault("SHOPPING_LOADTEST_DB_USERNAME", DEFAULT_DB_USERNAME));
        config.setPassword(envOrDefault("SHOPPING_LOADTEST_DB_PASSWORD", DEFAULT_DB_PASSWORD));
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(2);
        config.setPoolName("coupon-loadtest-coupon-seed");

        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int deletedRows = deleteExistingClaims(connection, coupons);
                int couponRows = upsertCoupons(connection, coupons);
                connection.commit();
                System.out.printf("Deleted existing user_coupon rows: %d%n", deletedRows);
                System.out.printf("Upserted loadtest coupon_template rows: %d%n", couponRows);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        for (int redisDatabase : redisDatabases()) {
            LettuceConnectionFactory connectionFactory = redisConnectionFactory(redisDatabase);
            try {
                connectionFactory.afterPropertiesSet();
                StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
                redisTemplate.afterPropertiesSet();
                writeCouponsToRedis(redisTemplate, coupons, redisDatabase);
            } finally {
                connectionFactory.destroy();
            }
        }

        System.out.printf("sameUserCouponId=%s%n", HybridIdCodec.toBase62(sameUserCoupon.id()));
        System.out.printf("differentUsersCouponId=%s%n", HybridIdCodec.toBase62(differentUsersCoupon.id()));
    }

    private static int deleteExistingClaims(Connection connection, List<CouponSeed> coupons) throws SQLException {
        String sql = """
                DELETE FROM user_coupon
                WHERE coupon_template_id IN (?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, coupons.get(0).id());
            statement.setBytes(2, coupons.get(1).id());
            return statement.executeUpdate();
        }
    }

    private static int upsertCoupons(Connection connection, List<CouponSeed> coupons) throws SQLException {
        String sql = """
                INSERT INTO coupon_template (
                    id,
                    coupon_code,
                    name,
                    discount_type,
                    threshold_amount_yuan,
                    discount_amount_yuan,
                    total_quantity,
                    remaining_quantity,
                    per_user_limit,
                    scope_type,
                    receive_start_at,
                    receive_end_at,
                    valid_start_at,
                    valid_end_at,
                    status,
                    version,
                    created_at,
                    updated_at
                )
                VALUES
                    (?, ?, ?, 'AMOUNT', 0, 1, ?, ?, 1, 'ALL', NOW() - INTERVAL '5 minutes', NOW() + INTERVAL '1 day', NOW() - INTERVAL '5 minutes', NOW() + INTERVAL '30 days', 'ACTIVE', 1, NOW(), NOW()),
                    (?, ?, ?, 'AMOUNT', 0, 1, ?, ?, 1, 'ALL', NOW() - INTERVAL '5 minutes', NOW() + INTERVAL '1 day', NOW() - INTERVAL '5 minutes', NOW() + INTERVAL '30 days', 'ACTIVE', 1, NOW(), NOW())
                ON CONFLICT (id) DO UPDATE
                SET coupon_code = EXCLUDED.coupon_code,
                    name = EXCLUDED.name,
                    discount_type = EXCLUDED.discount_type,
                    threshold_amount_yuan = EXCLUDED.threshold_amount_yuan,
                    discount_amount_yuan = EXCLUDED.discount_amount_yuan,
                    discount_rate = NULL,
                    max_discount_amount_yuan = NULL,
                    total_quantity = EXCLUDED.total_quantity,
                    remaining_quantity = EXCLUDED.remaining_quantity,
                    per_user_limit = EXCLUDED.per_user_limit,
                    scope_type = EXCLUDED.scope_type,
                    receive_start_at = EXCLUDED.receive_start_at,
                    receive_end_at = EXCLUDED.receive_end_at,
                    valid_start_at = EXCLUDED.valid_start_at,
                    valid_end_at = EXCLUDED.valid_end_at,
                    status = EXCLUDED.status,
                    version = coupon_template.version + 1,
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (CouponSeed coupon : coupons) {
                statement.setBytes(index++, coupon.id());
                statement.setString(index++, coupon.code());
                statement.setString(index++, coupon.name());
                statement.setInt(index++, coupon.stock());
                statement.setInt(index++, coupon.stock());
            }
            return statement.executeUpdate();
        }
    }

    private static LettuceConnectionFactory redisConnectionFactory(int redisDatabase) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                envOrDefault("SHOPPING_LOADTEST_REDIS_HOST", DEFAULT_REDIS_HOST),
                intEnvOrDefault("SHOPPING_LOADTEST_REDIS_PORT", DEFAULT_REDIS_PORT)
        );
        config.setDatabase(redisDatabase);
        config.setPassword(RedisPassword.of(envOrDefault("SHOPPING_LOADTEST_REDIS_PASSWORD", DEFAULT_REDIS_PASSWORD)));
        return new LettuceConnectionFactory(config);
    }

    private static List<Integer> redisDatabases() {
        String configured = System.getenv("SHOPPING_LOADTEST_REDIS_DATABASES");
        if (configured == null || configured.isBlank()) {
            return List.of(0, DEFAULT_REDIS_DATABASE, 2);
        }
        return java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .distinct()
                .toList();
    }

    private static void writeCouponsToRedis(StringRedisTemplate redisTemplate, List<CouponSeed> coupons, int redisDatabase) {
        long nowMs = System.currentTimeMillis();
        long receiveStartAt = nowMs - 300_000L;
        long receiveEndAt = nowMs + 86_400_000L;
        long validStartAt = nowMs - 300_000L;
        long validEndAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).toInstant().toEpochMilli();

        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                for (CouponSeed coupon : coupons) {
                    String couponId = HybridIdCodec.toBase62(coupon.id());
                    String templateKey = CouponRedisKeys.templateKey(couponId);
                    String stockKey = CouponRedisKeys.stockKey(couponId);
                    String scopeKey = CouponRedisKeys.scopeKey(couponId);
                    String claimedKey = CouponRedisKeys.claimedKey(couponId);
                    operations.delete(List.of(templateKey, stockKey, scopeKey, claimedKey));
                    operations.opsForHash().putAll(templateKey, redisTemplateHash(
                            couponId,
                            coupon.stock(),
                            receiveStartAt,
                            receiveEndAt,
                            validStartAt,
                            validEndAt
                    ));
                    operations.persist(templateKey);
                    operations.opsForValue().set(stockKey, String.valueOf(coupon.stock()));
                    operations.persist(stockKey);
                }
                return null;
            }
        });
        System.out.printf("Wrote Redis coupon runtime keys: %d, database=%d%n", coupons.size(), redisDatabase);
    }

    private static Map<String, String> redisTemplateHash(String couponId,
                                                         int stock,
                                                         long receiveStartAt,
                                                         long receiveEndAt,
                                                         long validStartAt,
                                                         long validEndAt) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("couponId", couponId);
        values.put("status", "ACTIVE");
        values.put("discountType", "AMOUNT");
        values.put("thresholdAmountYuan", BigDecimal.ZERO.toPlainString());
        values.put("discountAmountYuan", BigDecimal.ONE.toPlainString());
        values.put("discountRate", "");
        values.put("maxDiscountAmountYuan", "");
        values.put("perUserLimit", "1");
        values.put("scopeType", "ALL");
        values.put("receiveStartAtEpochMs", String.valueOf(receiveStartAt));
        values.put("receiveEndAtEpochMs", String.valueOf(receiveEndAt));
        values.put("validStartAtEpochMs", String.valueOf(validStartAt));
        values.put("validEndAtEpochMs", String.valueOf(validEndAt));
        values.put("version", "1");
        values.put("totalQuantity", String.valueOf(stock));
        values.put("remainingQuantity", String.valueOf(stock));
        return values;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static int intEnvOrDefault(String name, int defaultValue) {
        String value = envOrDefault(name, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer: " + value, e);
        }
    }

    private record CouponSeed(byte[] id, String code, String name, int stock) {
    }
}
