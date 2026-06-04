package com.example.ShoppingSystem.tools.loadtest;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.avatar.AvatarMetadataUtils;
import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.JwtUtils;
import com.example.ShoppingSystem.security.token.AuthUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class OrderAccessTokenSkuCsvExportMain {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/shopping";
    private static final String DEFAULT_DB_USERNAME = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "123456";
    private static final String DEFAULT_REDIS_HOST = "127.0.0.1";
    private static final int DEFAULT_REDIS_PORT = 6380;
    private static final String DEFAULT_REDIS_PASSWORD = "123456";
    private static final int DEFAULT_REDIS_DATABASE = 1;
    private static final long DEFAULT_AUTH_CONTEXT_TTL_SECONDS = 11100L;
    private static final String AUTH_USER_CONTEXT_KEY_PREFIX = "auth:user:context:";
    private static final String DEFAULT_MODE = "single-hot";
    private static final String DEFAULT_OUTPUT = "loadtest-output/order-create-token-sku.csv";
    private static final String DEFAULT_EXPIRES_AT = "2026-07-31T23:59:59-07:00";
    private static final long DEFAULT_MIN_USER_ID = 1L;
    private static final long DEFAULT_MAX_USER_ID = 500L;
    private static final int DEFAULT_LIMIT = 500;
    private static final int DEFAULT_SPREAD_SKU_COUNT = 50;
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_JTI = "jti";
    private static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    public static void main(String[] args) throws Exception {
        Mode mode = Mode.parse(arg(args, 0, DEFAULT_MODE));
        Path outputPath = Path.of(arg(args, 1, DEFAULT_OUTPUT));
        ExpirationSetting expiration = parseExpiration(args, 2, DEFAULT_EXPIRES_AT);
        long minUserId = parsePositiveLong(args, 3, DEFAULT_MIN_USER_ID, "minUserId");
        long maxUserId = parsePositiveLong(args, 4, DEFAULT_MAX_USER_ID, "maxUserId");
        int limit = parsePositiveInt(args, 5, DEFAULT_LIMIT, "limit");
        List<String> explicitSkuIds = parseSkuIds(arg(args, 6, ""));
        if (minUserId > maxUserId) {
            throw new IllegalArgumentException("minUserId must be less than or equal to maxUserId.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(envOrDefault("SHOPPING_LOADTEST_DB_URL", DEFAULT_DB_URL));
        config.setUsername(envOrDefault("SHOPPING_LOADTEST_DB_USERNAME", DEFAULT_DB_USERNAME));
        config.setPassword(envOrDefault("SHOPPING_LOADTEST_DB_PASSWORD", DEFAULT_DB_PASSWORD));
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(2);
        config.setPoolName("order-token-sku-export");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            List<TokenUserRow> users = loadActiveUsers(
                    dataSource,
                    mode == Mode.SAME_USER ? 1 : limit,
                    minUserId,
                    maxUserId
            );
            if (users.isEmpty()) {
                throw new IllegalStateException("No ACTIVE users with token_version were found.");
            }
            List<String> skuIds = explicitSkuIds.isEmpty()
                    ? loadHotSkuIds(dataSource, mode.requiredSkuCount())
                    : explicitSkuIds;
            mode.validateSkuIds(skuIds);

            JwtUtils jwtUtils = new JwtUtils();
            ObjectMapper objectMapper = new ObjectMapper();
            List<TokenSkuCsvRow> rows = toRows(mode, users, skuIds, jwtUtils, expiration.ttlSeconds(), limit);
            writeCsv(rows, outputPath);
            prewarmAuthContexts(users, objectMapper);

            System.out.printf("Exported %d order loadtest rows to %s%n", rows.size(), outputPath.toAbsolutePath());
            System.out.printf("Mode: %s%n", mode.value);
            System.out.printf("User id range: %d-%d%n", minUserId, maxUserId);
            System.out.printf("SKU count: %d%n", skuIds.size());
            System.out.printf("Token expires at: %s%n", expiration.expiresAt());
            System.out.printf("TTL seconds: %d%n", expiration.ttlSeconds());
        }
    }

    private static List<TokenSkuCsvRow> toRows(Mode mode,
                                               List<TokenUserRow> users,
                                               List<String> skuIds,
                                               JwtUtils jwtUtils,
                                               long ttlSeconds,
                                               int limit) {
        List<TokenSkuCsvRow> rows = new ArrayList<>(limit);
        if (mode == Mode.SAME_USER) {
            TokenUserRow user = users.getFirst();
            String accessToken = generateAccessToken(jwtUtils, user, ttlSeconds);
            String skuId = skuIds.getFirst();
            for (int index = 0; index < limit; index += 1) {
                rows.add(new TokenSkuCsvRow(user.userId(), accessToken, skuId));
            }
            return rows;
        }
        for (int index = 0; index < users.size() && rows.size() < limit; index += 1) {
            TokenUserRow user = users.get(index);
            String skuId = mode == Mode.SPREAD_HOT
                    ? skuIds.get(index % skuIds.size())
                    : skuIds.getFirst();
            rows.add(new TokenSkuCsvRow(user.userId(), generateAccessToken(jwtUtils, user, ttlSeconds), skuId));
        }
        return rows;
    }

    private static List<TokenUserRow> loadActiveUsers(DataSource dataSource,
                                                      int limit,
                                                      long minUserId,
                                                      long maxUserId) throws SQLException {
        String sql = """
                SELECT i.user_id,
                       i.token_version,
                       COALESCE(i.email, '') AS email,
                       COALESCE(i.phone, '') AS phone,
                       i.status,
                       COALESCE(p.username, '') AS username,
                       COALESCE(p.first_name, '') AS first_name,
                       COALESCE(p.last_name, '') AS last_name,
                       COALESCE(p.gender, '') AS gender,
                       p.avatar::text AS avatar
                FROM user_login_identity i
                LEFT JOIN user_profile p ON p.id = i.user_id
                WHERE i.status = 'ACTIVE'
                  AND i.token_version IS NOT NULL
                  AND i.token_version <> ''
                  AND i.user_id BETWEEN ? AND ?
                ORDER BY i.user_id
                LIMIT ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, minUserId);
            statement.setLong(2, maxUserId);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TokenUserRow> users = new ArrayList<>();
                while (resultSet.next()) {
                    users.add(new TokenUserRow(
                            resultSet.getLong("user_id"),
                            resultSet.getString("token_version"),
                            resultSet.getString("email"),
                            resultSet.getString("phone"),
                            resultSet.getString("status"),
                            resultSet.getString("username"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("gender"),
                            resultSet.getString("avatar")
                    ));
                }
                return users;
            }
        }
    }

    private static List<String> loadHotSkuIds(DataSource dataSource, int limit) throws SQLException {
        String sql = """
                SELECT encode(h.sku_id, 'hex') AS sku_id_hex
                FROM product_hot_sku h
                INNER JOIN product_sku s ON s.id = h.sku_id
                INNER JOIN product_spu p ON p.id = h.spu_id
                INNER JOIN product_category c ON c.id = p.category_id
                WHERE h.status = 'ENABLED'
                  AND s.status = 'ACTIVE'
                  AND p.status = 'ACTIVE'
                  AND c.status = 'ACTIVE'
                  AND (h.start_at IS NULL OR h.start_at <= NOW())
                  AND (h.end_at IS NULL OR h.end_at > NOW())
                ORDER BY h.updated_at DESC, h.created_at DESC, h.sku_id
                LIMIT ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> skuIds = new ArrayList<>();
                while (resultSet.next()) {
                    skuIds.add(HybridIdCodec.hexToBase62(resultSet.getString("sku_id_hex")));
                }
                return skuIds;
            }
        }
    }

    private static String generateAccessToken(JwtUtils jwtUtils, TokenUserRow user, long ttlSeconds) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(user.userId()));
        claims.put(CLAIM_TYPE, ACCESS_TOKEN_TYPE);
        claims.put(CLAIM_JTI, IdUtil.nanoId(24));
        claims.put(CLAIM_TOKEN_VERSION, user.tokenVersion());
        return jwtUtils.generateToken(claims, ttlSeconds).join();
    }

    private static void prewarmAuthContexts(List<TokenUserRow> users, ObjectMapper objectMapper) throws Exception {
        if (users == null || users.isEmpty()) {
            return;
        }
        long ttlSeconds = parsePositiveLong(
                envOrDefault("SHOPPING_LOADTEST_AUTH_CONTEXT_TTL_SECONDS", String.valueOf(DEFAULT_AUTH_CONTEXT_TTL_SECONDS)),
                "authContextTtlSeconds"
        );
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(envOrDefault("SHOPPING_LOADTEST_REDIS_HOST", DEFAULT_REDIS_HOST))
                .withPort(parsePositiveInt(envOrDefault("SHOPPING_LOADTEST_REDIS_PORT", String.valueOf(DEFAULT_REDIS_PORT)), "redisPort"))
                .withDatabase(parseNonNegativeInt(envOrDefault("SHOPPING_LOADTEST_REDIS_DATABASE", String.valueOf(DEFAULT_REDIS_DATABASE)), "redisDatabase"));
        String password = envOrDefault("SHOPPING_LOADTEST_REDIS_PASSWORD", DEFAULT_REDIS_PASSWORD);
        if (!password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }

        RedisClient client = RedisClient.create(builder.build());
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            commands.setAutoFlushCommands(false);
            List<RedisFuture<?>> futures = new ArrayList<>(users.size());
            for (TokenUserRow user : users) {
                String key = AUTH_USER_CONTEXT_KEY_PREFIX + user.userId();
                String value = objectMapper.writeValueAsString(toAuthUserContext(user, objectMapper));
                futures.add(commands.setex(key, ttlSeconds, value));
            }
            commands.flushCommands();
            for (RedisFuture<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            commands.setAutoFlushCommands(true);
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
        System.out.printf("Prewarmed %d auth user contexts in Redis DB %d%n", users.size(), resolveRedisDatabase());
    }

    private static AuthUserContext toAuthUserContext(TokenUserRow user, ObjectMapper objectMapper) {
        String email = blankToDefault(user.email(), "");
        String phone = blankToDefault(user.phone(), "");
        String username = blankToDefault(user.username(), "");
        if (username.isBlank()) {
            username = blankToDefault(email, phone);
        }
        String avatarUrl = user.avatar() == null || user.avatar().isBlank()
                ? ""
                : AvatarMetadataUtils.extractUrl(user.avatar(), objectMapper);
        return new AuthUserContext(
                user.userId(),
                username,
                blankToDefault(user.firstName(), ""),
                blankToDefault(user.lastName(), ""),
                blankToDefault(email, phone),
                email,
                phone,
                blankToDefault(user.status(), "ACTIVE"),
                blankToDefault(user.gender(), ""),
                blankToDefault(avatarUrl, ""),
                blankToDefault(user.tokenVersion(), ""),
                "L1",
                Set.of("USER")
        );
    }

    private static void writeCsv(List<TokenSkuCsvRow> rows, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("userId,accessToken,skuId");
            writer.newLine();
            for (TokenSkuCsvRow row : rows) {
                writer.write(csv(row.userId()));
                writer.write(',');
                writer.write(csv(row.accessToken()));
                writer.write(',');
                writer.write(csv(row.skuId()));
                writer.newLine();
            }
        }
    }

    private static List<String> parseSkuIds(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        List<String> skuIds = new ArrayList<>();
        for (String item : value.split(",")) {
            String skuId = item.trim();
            if (!skuId.matches(HybridIdCodec.BASE62_PATTERN)) {
                throw new IllegalArgumentException("skuIds must be comma-separated Base62 values: " + rawValue);
            }
            HybridIdCodec.fromBase62(skuId);
            skuIds.add(skuId);
        }
        return List.copyOf(skuIds);
    }

    private static String csv(Object value) {
        String text = String.valueOf(value);
        if (!text.contains(",") && !text.contains("\"") && !text.contains("\n") && !text.contains("\r")) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String arg(String[] args, int index, String defaultValue) {
        if (args.length <= index || args[index] == null || args[index].isBlank()) {
            return defaultValue;
        }
        return args[index].trim();
    }

    private static int parsePositiveInt(String[] args, int index, int defaultValue, String name) {
        String value = arg(args, index, String.valueOf(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(name + " must be a positive integer: " + value);
    }

    private static int parsePositiveInt(String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(name + " must be a positive integer: " + value);
    }

    private static int parseNonNegativeInt(String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(name + " must be a non-negative integer: " + value);
    }

    private static long parsePositiveLong(String[] args, int index, long defaultValue, String name) {
        String value = arg(args, index, String.valueOf(defaultValue));
        return parsePositiveLong(value, name);
    }

    private static long parsePositiveLong(String value, String name) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(name + " must be a positive long: " + value);
    }

    private static int resolveRedisDatabase() {
        return parseNonNegativeInt(
                envOrDefault("SHOPPING_LOADTEST_REDIS_DATABASE", String.valueOf(DEFAULT_REDIS_DATABASE)),
                "redisDatabase"
        );
    }

    private static ExpirationSetting parseExpiration(String[] args, int index, String defaultValue) {
        String value = arg(args, index, defaultValue);
        try {
            long ttlSeconds = Long.parseLong(value);
            if (ttlSeconds <= 0) {
                throw new IllegalArgumentException("ttlSeconds must be positive: " + value);
            }
            Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
            return new ExpirationSetting(ttlSeconds, expiresAt);
        } catch (NumberFormatException ignored) {
            Instant expiresAt = OffsetDateTime.parse(value).toInstant();
            long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
            if (ttlSeconds <= 0) {
                throw new IllegalArgumentException("expiresAt must be in the future: " + value);
            }
            return new ExpirationSetting(ttlSeconds, expiresAt);
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private enum Mode {
        SINGLE_HOT("single-hot"),
        SAME_USER("same-user"),
        SPREAD_HOT("spread-hot");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        private static Mode parse(String rawValue) {
            String value = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("mode must be single-hot, same-user, or spread-hot: " + rawValue);
        }

        private int requiredSkuCount() {
            return this == SPREAD_HOT ? DEFAULT_SPREAD_SKU_COUNT : 1;
        }

        private void validateSkuIds(List<String> skuIds) {
            if (skuIds == null || skuIds.isEmpty()) {
                throw new IllegalStateException("No enabled hot SKU was found.");
            }
            if (this == SPREAD_HOT && skuIds.size() < DEFAULT_SPREAD_SKU_COUNT) {
                throw new IllegalStateException("spread-hot requires at least " + DEFAULT_SPREAD_SKU_COUNT + " hot SKUs.");
            }
        }
    }

    private record TokenUserRow(Long userId,
                                String tokenVersion,
                                String email,
                                String phone,
                                String status,
                                String username,
                                String firstName,
                                String lastName,
                                String gender,
                                String avatar) {
    }

    private record TokenSkuCsvRow(Long userId, String accessToken, String skuId) {
    }

    private record ExpirationSetting(long ttlSeconds, Instant expiresAt) {
    }
}
