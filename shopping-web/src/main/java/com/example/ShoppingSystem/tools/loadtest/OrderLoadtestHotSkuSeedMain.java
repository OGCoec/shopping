package com.example.ShoppingSystem.tools.loadtest;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OrderLoadtestHotSkuSeedMain {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/shopping";
    private static final String DEFAULT_DB_USERNAME = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "123456";
    private static final String DEFAULT_REDIS_HOST = "127.0.0.1";
    private static final int DEFAULT_REDIS_PORT = 6380;
    private static final String DEFAULT_REDIS_PASSWORD = "123456";
    private static final int DEFAULT_REDIS_DATABASE = 1;
    private static final int DEFAULT_SKU_COUNT = 1;
    private static final int DEFAULT_STOCK_PER_SKU = 50;
    private static final long DEFAULT_CATEGORY_ID = 990001L;
    private static final long DEFAULT_SPU_ID = 990001L;
    private static final String DEFAULT_OUTPUT = "loadtest-output/order-hot-sku-ids.csv";
    private static final String HOT_SKU_META_KEY_PREFIX = "shopping:product:hot-sku:meta:";
    private static final String HOT_SKU_STOCK_KEY_PREFIX = "shopping:product:hot-sku:stock:";
    private static final String HOT_SKU_USER_KEY_PREFIX = "shopping:order:hot-sku:user:";

    public static void main(String[] args) throws Exception {
        int skuCount = parsePositiveInt(args, 0, DEFAULT_SKU_COUNT, "skuCount");
        int stockPerSku = parsePositiveInt(args, 1, DEFAULT_STOCK_PER_SKU, "stockPerSku");
        long categoryId = parsePositiveLong(args, 2, DEFAULT_CATEGORY_ID, "categoryId");
        long spuId = parsePositiveLong(args, 3, DEFAULT_SPU_ID, "spuId");
        Path outputPath = Path.of(arg(args, 4, DEFAULT_OUTPUT));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(envOrDefault("SHOPPING_LOADTEST_DB_URL", DEFAULT_DB_URL));
        config.setUsername(envOrDefault("SHOPPING_LOADTEST_DB_USERNAME", DEFAULT_DB_USERNAME));
        config.setPassword(envOrDefault("SHOPPING_LOADTEST_DB_PASSWORD", DEFAULT_DB_PASSWORD));
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(2);
        config.setPoolName("order-hot-sku-seed");

        List<SeedSkuInput> inputs = buildInputs(skuCount, stockPerSku);
        List<RedisHotSkuItem> redisItems;
        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertCategory(connection, categoryId);
                upsertSpu(connection, spuId, categoryId);
                List<SeedSkuRow> skuRows = upsertSkus(connection, spuId, inputs);
                redisItems = upsertHotSkus(connection, spuId, stockPerSku, skuRows);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        writeHotSkusToRedis(spuId, redisItems);
        writeCsv(redisItems, outputPath);
        System.out.printf("Seeded order loadtest hot SKUs: %d%n", redisItems.size());
        System.out.printf("Stock per SKU: %d%n", stockPerSku);
        System.out.printf("Output: %s%n", outputPath.toAbsolutePath());
        if (!redisItems.isEmpty()) {
            System.out.printf("First SKU: %s%n", redisItems.getFirst().skuId());
        }
    }

    private static List<SeedSkuInput> buildInputs(int skuCount, int stockPerSku) {
        HybridSemaphoreIdWorker idWorker = new HybridSemaphoreIdWorker(1, 1);
        List<SeedSkuInput> rows = new ArrayList<>(skuCount);
        for (int index = 1; index <= skuCount; index += 1) {
            rows.add(new SeedSkuInput(
                    HybridIdCodec.toHex(idWorker.nextId()),
                    String.format("ORDER_LOADTEST_SKU_%03d", index),
                    String.format("Order Loadtest SKU %03d", index),
                    stockPerSku
            ));
        }
        return rows;
    }

    private static void upsertCategory(Connection connection, long categoryId) throws SQLException {
        String sql = """
                INSERT INTO product_category (
                    id, parent_id, name, code, level, path, sort_order, icon_urls, description, status, is_leaf, created_at, updated_at
                )
                VALUES (?, 0, 'Order Loadtest', 'ORDER_LOADTEST', 1, ?, 0, '[]'::jsonb, 'order create loadtest category', 'ACTIVE', true, NOW(), NOW())
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    code = EXCLUDED.code,
                    path = EXCLUDED.path,
                    status = 'ACTIVE',
                    is_leaf = true,
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, categoryId);
            statement.setString(2, "/" + categoryId + "/");
            statement.executeUpdate();
        }
    }

    private static void upsertSpu(Connection connection, long spuId, long categoryId) throws SQLException {
        String sql = """
                INSERT INTO product_spu (
                    id, category_id, name, subtitle, brand_name, main_image_url, status, created_at, updated_at
                )
                VALUES (?, ?, 'Order Loadtest Product', 'hot sku loadtest product', 'Loadtest', '', 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO UPDATE
                SET category_id = EXCLUDED.category_id,
                    name = EXCLUDED.name,
                    subtitle = EXCLUDED.subtitle,
                    brand_name = EXCLUDED.brand_name,
                    status = 'ACTIVE',
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, spuId);
            statement.setLong(2, categoryId);
            statement.executeUpdate();
        }
    }

    private static List<SeedSkuRow> upsertSkus(Connection connection,
                                               long spuId,
                                               List<SeedSkuInput> inputs) throws SQLException, JsonProcessingException {
        String sql = """
                WITH raw AS (
                    SELECT *
                    FROM jsonb_to_recordset(?::jsonb) AS x(
                        id_hex text,
                        sku_code text,
                        sku_name text,
                        stock_quantity integer
                    )
                ),
                upserted AS (
                    INSERT INTO product_sku (
                        id, spu_id, sku_code, sku_name, spec_json, sku_image_url, price_yuan, original_price_yuan,
                        stock_quantity, status, created_at, updated_at
                    )
                    SELECT decode(id_hex, 'hex'),
                           ?,
                           sku_code,
                           sku_name,
                           '{"loadtest":true}'::jsonb,
                           '[]',
                           9.90,
                           19.90,
                           stock_quantity,
                           'ACTIVE',
                           NOW(),
                           NOW()
                    FROM raw
                    ON CONFLICT (sku_code) DO UPDATE
                    SET spu_id = EXCLUDED.spu_id,
                        sku_name = EXCLUDED.sku_name,
                        stock_quantity = EXCLUDED.stock_quantity,
                        status = 'ACTIVE',
                        updated_at = NOW()
                    RETURNING encode(id, 'hex') AS sku_id_hex, sku_code, stock_quantity
                )
                SELECT sku_id_hex, sku_code, stock_quantity
                FROM upserted
                ORDER BY sku_code
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toJson(inputs));
            statement.setLong(2, spuId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SeedSkuRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new SeedSkuRow(
                            resultSet.getString("sku_id_hex"),
                            HybridIdCodec.hexToBase62(resultSet.getString("sku_id_hex")),
                            resultSet.getString("sku_code"),
                            resultSet.getInt("stock_quantity")
                    ));
                }
                return rows;
            }
        }
    }

    private static List<RedisHotSkuItem> upsertHotSkus(Connection connection,
                                                       long spuId,
                                                       int stockPerSku,
                                                       List<SeedSkuRow> skuRows) throws SQLException, JsonProcessingException {
        HybridSemaphoreIdWorker idWorker = new HybridSemaphoreIdWorker(1, 2);
        List<Map<String, Object>> payload = new ArrayList<>(skuRows.size());
        for (SeedSkuRow row : skuRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hot_id_hex", HybridIdCodec.toHex(idWorker.nextId()));
            item.put("sku_id_hex", row.skuIdHex());
            item.put("stock_quantity", stockPerSku);
            payload.add(item);
        }
        String sql = """
                WITH raw AS (
                    SELECT *
                    FROM jsonb_to_recordset(?::jsonb) AS x(
                        hot_id_hex text,
                        sku_id_hex text,
                        stock_quantity integer
                    )
                ),
                upserted AS (
                    INSERT INTO product_hot_sku (
                        id, spu_id, sku_id, stock_quantity, remaining_quantity, status, start_at, end_at, version, created_at, updated_at
                    )
                    SELECT decode(hot_id_hex, 'hex'),
                           ?,
                           decode(sku_id_hex, 'hex'),
                           stock_quantity,
                           stock_quantity,
                           'ENABLED',
                           NOW() - INTERVAL '1 minute',
                           NOW() + INTERVAL '1 day',
                           1,
                           NOW(),
                           NOW()
                    FROM raw
                    ON CONFLICT (sku_id) DO UPDATE
                    SET spu_id = EXCLUDED.spu_id,
                        stock_quantity = EXCLUDED.stock_quantity,
                        remaining_quantity = EXCLUDED.remaining_quantity,
                        status = 'ENABLED',
                        start_at = EXCLUDED.start_at,
                        end_at = EXCLUDED.end_at,
                        version = product_hot_sku.version + 1,
                        updated_at = NOW()
                    RETURNING encode(sku_id, 'hex') AS sku_id_hex,
                              stock_quantity,
                              remaining_quantity,
                              status,
                              FLOOR(EXTRACT(EPOCH FROM start_at) * 1000)::bigint AS start_at_epoch_ms,
                              FLOOR(EXTRACT(EPOCH FROM end_at) * 1000)::bigint AS end_at_epoch_ms,
                              version
                )
                SELECT sku_id_hex,
                       stock_quantity,
                       remaining_quantity,
                       status,
                       start_at_epoch_ms,
                       end_at_epoch_ms,
                       version
                FROM upserted
                ORDER BY sku_id_hex
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toJson(payload));
            statement.setLong(2, spuId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RedisHotSkuItem> rows = new ArrayList<>();
                while (resultSet.next()) {
                    String skuIdHex = resultSet.getString("sku_id_hex");
                    rows.add(new RedisHotSkuItem(
                            HybridIdCodec.hexToBase62(skuIdHex),
                            resultSet.getInt("stock_quantity"),
                            resultSet.getInt("remaining_quantity"),
                            resultSet.getString("status"),
                            resultSet.getLong("start_at_epoch_ms"),
                            resultSet.getLong("end_at_epoch_ms"),
                            resultSet.getLong("version")
                    ));
                }
                return rows;
            }
        }
    }

    private static void writeHotSkusToRedis(long spuId, List<RedisHotSkuItem> items) throws Exception {
        if (items == null || items.isEmpty()) {
            return;
        }
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
            List<RedisFuture<?>> futures = new ArrayList<>(items.size() * 5);
            for (RedisHotSkuItem item : items) {
                String metaKey = HOT_SKU_META_KEY_PREFIX + item.skuId();
                String stockKey = HOT_SKU_STOCK_KEY_PREFIX + item.skuId();
                String userKey = HOT_SKU_USER_KEY_PREFIX + item.skuId();
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("spuId", String.valueOf(spuId));
                meta.put("skuId", item.skuId());
                meta.put("status", item.status());
                meta.put("startAtEpochMs", String.valueOf(item.startAtEpochMs()));
                meta.put("endAtEpochMs", String.valueOf(item.endAtEpochMs()));
                meta.put("stockQuantity", String.valueOf(item.stockQuantity()));
                meta.put("version", String.valueOf(item.version()));
                futures.add(commands.hset(metaKey, meta));
                futures.add(commands.persist(metaKey));
                futures.add(commands.set(stockKey, String.valueOf(item.remainingQuantity())));
                futures.add(commands.persist(stockKey));
                futures.add(commands.del(userKey));
            }
            commands.flushCommands();
            for (RedisFuture<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            commands.setAutoFlushCommands(true);
        } finally {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }

    private static void writeCsv(List<RedisHotSkuItem> rows, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("skuId,stockQuantity,remainingQuantity");
            writer.newLine();
            for (RedisHotSkuItem row : rows) {
                writer.write(row.skuId());
                writer.write(',');
                writer.write(String.valueOf(row.stockQuantity()));
                writer.write(',');
                writer.write(String.valueOf(row.remainingQuantity()));
                writer.newLine();
            }
        }
    }

    private static String toJson(Object value) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(value);
    }

    private static String arg(String[] args, int index, String defaultValue) {
        if (args.length <= index || args[index] == null || args[index].isBlank()) {
            return defaultValue;
        }
        return args[index].trim();
    }

    private static int parsePositiveInt(String[] args, int index, int defaultValue, String name) {
        return parsePositiveInt(arg(args, index, String.valueOf(defaultValue)), name);
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
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(name + " must be a positive long: " + value);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private record SeedSkuInput(String id_hex, String sku_code, String sku_name, int stock_quantity) {
    }

    private record SeedSkuRow(String skuIdHex, String skuId, String skuCode, int stockQuantity) {
    }

    private record RedisHotSkuItem(String skuId,
                                   int stockQuantity,
                                   int remainingQuantity,
                                   String status,
                                   long startAtEpochMs,
                                   long endAtEpochMs,
                                   long version) {
    }
}
