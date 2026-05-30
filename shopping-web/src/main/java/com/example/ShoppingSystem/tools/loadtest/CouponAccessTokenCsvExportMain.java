package com.example.ShoppingSystem.tools.loadtest;

import cn.hutool.core.util.IdUtil;
import com.example.ShoppingSystem.Utils.JwtUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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
import java.util.Map;

public class CouponAccessTokenCsvExportMain {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/shopping";
    private static final String DEFAULT_DB_USERNAME = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "123456";
    private static final int DEFAULT_LIMIT = 500;
    private static final long DEFAULT_TTL_SECONDS = 7200L;
    private static final String DEFAULT_OUTPUT = "loadtest-output/coupon-users-token.csv";
    private static final long DEFAULT_MIN_USER_ID = 1L;
    private static final long DEFAULT_MAX_USER_ID = Long.MAX_VALUE;
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_JTI = "jti";
    private static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    public static void main(String[] args) throws Exception {
        int limit = parsePositiveInt(args, 0, DEFAULT_LIMIT, "limit");
        Path outputPath = Path.of(arg(args, 1, DEFAULT_OUTPUT));
        ExpirationSetting expiration = parseExpiration(args, 2, DEFAULT_TTL_SECONDS);
        long minUserId = parsePositiveLong(args, 3, DEFAULT_MIN_USER_ID, "minUserId");
        long maxUserId = parsePositiveLong(args, 4, DEFAULT_MAX_USER_ID, "maxUserId");
        if (minUserId > maxUserId) {
            throw new IllegalArgumentException("minUserId must be less than or equal to maxUserId.");
        }
        Path sameUserOutputPath = sibling(outputPath, "same-user-token.csv");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(envOrDefault("SHOPPING_LOADTEST_DB_URL", DEFAULT_DB_URL));
        config.setUsername(envOrDefault("SHOPPING_LOADTEST_DB_USERNAME", DEFAULT_DB_USERNAME));
        config.setPassword(envOrDefault("SHOPPING_LOADTEST_DB_PASSWORD", DEFAULT_DB_PASSWORD));
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(2);
        config.setPoolName("coupon-token-export");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            List<TokenUserRow> users = loadActiveUsers(dataSource, limit, minUserId, maxUserId);
            if (users.isEmpty()) {
                throw new IllegalStateException("No ACTIVE users with token_version were found.");
            }

            JwtUtils jwtUtils = new JwtUtils();
            List<TokenCsvRow> tokenRows = users.stream()
                    .map(user -> new TokenCsvRow(user.userId(), generateAccessToken(jwtUtils, user, expiration.ttlSeconds())))
                    .toList();

            writeCsv(tokenRows, outputPath);
            writeCsv(List.of(tokenRows.getFirst()), sameUserOutputPath);

            System.out.printf("Exported %d access tokens to %s%n", tokenRows.size(), outputPath.toAbsolutePath());
            System.out.printf("Exported same-user token to %s%n", sameUserOutputPath.toAbsolutePath());
            System.out.printf("User id range: %d-%d%n", minUserId, maxUserId);
            System.out.printf("Token expires at: %s%n", expiration.expiresAt());
            System.out.printf("TTL seconds: %d%n", expiration.ttlSeconds());
        }
    }

    private static List<TokenUserRow> loadActiveUsers(DataSource dataSource,
                                                      int limit,
                                                      long minUserId,
                                                      long maxUserId) throws SQLException {
        String sql = """
                SELECT user_id, token_version
                FROM user_login_identity
                WHERE status = 'ACTIVE'
                  AND token_version IS NOT NULL
                  AND token_version <> ''
                  AND user_id BETWEEN ? AND ?
                ORDER BY user_id
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
                            resultSet.getString("token_version")
                    ));
                }
                return users;
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

    private static void writeCsv(List<TokenCsvRow> rows, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("userId,accessToken");
            writer.newLine();
            for (TokenCsvRow row : rows) {
                writer.write(csv(row.userId()));
                writer.write(',');
                writer.write(csv(row.accessToken()));
                writer.newLine();
            }
        }
    }

    private static String csv(Object value) {
        String text = String.valueOf(value);
        if (!text.contains(",") && !text.contains("\"") && !text.contains("\n") && !text.contains("\r")) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static Path sibling(Path outputPath, String filename) {
        Path parent = outputPath.getParent();
        if (parent == null) {
            return Path.of(filename);
        }
        return parent.resolve(filename);
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

    private static ExpirationSetting parseExpiration(String[] args, int index, long defaultTtlSeconds) {
        String value = arg(args, index, String.valueOf(defaultTtlSeconds));
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

    private record TokenUserRow(Long userId, String tokenVersion) {
    }

    private record TokenCsvRow(Long userId, String accessToken) {
    }

    private record ExpirationSetting(long ttlSeconds, Instant expiresAt) {
    }
}
