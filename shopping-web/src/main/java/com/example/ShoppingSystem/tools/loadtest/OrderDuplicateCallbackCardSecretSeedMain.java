package com.example.ShoppingSystem.tools.loadtest;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.Utils.HybridSemaphoreIdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OrderDuplicateCallbackCardSecretSeedMain {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/shopping";
    private static final String DEFAULT_DB_USERNAME = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "123456";
    private static final int DEFAULT_ORDER_COUNT = 2;
    private static final int DEFAULT_QUANTITY_PER_ORDER = 2;
    private static final int DEFAULT_DUPLICATE_CALLBACKS_PER_ORDER = 5;
    private static final String DEFAULT_OUTPUT = "loadtest-output/order-duplicate-callback-card-secret-input.csv";
    private static final long DEFAULT_USER_ID_START = 9_900_001L;
    private static final long LOADTEST_CATEGORY_ID = 990_034L;
    private static final long LOADTEST_SPU_ID = 990_034L;
    private static final String LOADTEST_SKU_CODE = "ORDER_DUPLICATE_CALLBACK_CARD_SECRET_SKU";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        int orderCount = parsePositiveInt(args, 0, DEFAULT_ORDER_COUNT, "orderCount");
        int quantityPerOrder = parsePositiveInt(args, 1, DEFAULT_QUANTITY_PER_ORDER, "quantityPerOrder");
        int duplicateCallbacksPerOrder = parsePositiveInt(args, 2, DEFAULT_DUPLICATE_CALLBACKS_PER_ORDER, "duplicateCallbacksPerOrder");
        Path outputPath = Path.of(arg(args, 3, DEFAULT_OUTPUT));
        String runId = arg(args, 4, defaultRunId());
        boolean seedIfShortage = parseBoolean(args, 5, false, "seedIfShortage");
        long userIdStart = parsePositiveLong(args, 6, DEFAULT_USER_ID_START, "userIdStart");

        int expectedDeliveryUnits = Math.multiplyExact(orderCount, quantityPerOrder);
        int requiredUnusedInventory = Math.multiplyExact(expectedDeliveryUnits, duplicateCallbacksPerOrder);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(envOrDefault("SHOPPING_LOADTEST_DB_URL", DEFAULT_DB_URL));
        config.setUsername(envOrDefault("SHOPPING_LOADTEST_DB_USERNAME", DEFAULT_DB_USERNAME));
        config.setPassword(envOrDefault("SHOPPING_LOADTEST_DB_PASSWORD", DEFAULT_DB_PASSWORD));
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(2);
        config.setPoolName("order-duplicate-callback-card-secret-seed");

        SelectedSku selectedSku;
        List<OrderSeedInput> orderRows;
        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                selectedSku = selectOrSeedSku(connection, runId, requiredUnusedInventory, seedIfShortage);
                orderRows = buildOrderRows(orderCount, quantityPerOrder, runId, selectedSku, userIdStart);
                InsertSummary insertSummary = insertOrdersAndItems(connection, orderRows);
                if (insertSummary.orderCount() != orderCount || insertSummary.itemCount() != orderCount) {
                    throw new IllegalStateException("Inserted order count mismatch. orders="
                            + insertSummary.orderCount() + ", items=" + insertSummary.itemCount());
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        List<CallbackCsvRow> csvRows = buildCallbackRows(runId, orderRows, duplicateCallbacksPerOrder);
        writeCsv(csvRows, outputPath);

        System.out.printf("Seeded duplicate callback card-secret orders: %d%n", orderRows.size());
        System.out.printf("Quantity per order: %d%n", quantityPerOrder);
        System.out.printf("Duplicate callbacks per order: %d%n", duplicateCallbacksPerOrder);
        System.out.printf("Expected delivery units: %d%n", expectedDeliveryUnits);
        System.out.printf("Required UNUSED inventory for over-delivery exposure: %d%n", requiredUnusedInventory);
        System.out.printf("Selected SKU: %s%n", selectedSku.skuId());
        System.out.printf("Available UNUSED inventory before callbacks: %d%n", selectedSku.availableUnused());
        System.out.printf("RunId: %s%n", runId);
        System.out.printf("Output: %s%n", outputPath.toAbsolutePath());
    }

    private static SelectedSku selectOrSeedSku(Connection connection,
                                               String runId,
                                               int requiredUnusedInventory,
                                               boolean seedIfShortage) throws Exception {
        Optional<SelectedSku> existing = findEligibleSku(connection, requiredUnusedInventory);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!seedIfShortage) {
            throw new IllegalStateException("No ACTIVE SKU has at least " + requiredUnusedInventory
                    + " UNUSED card_secret_inventory rows. Re-run with seedIfShortage=true if test inventory should be inserted.");
        }
        upsertLoadtestCategory(connection);
        upsertLoadtestSpu(connection);
        SelectedSku seededSku = upsertLoadtestSku(connection, requiredUnusedInventory);
        int inserted = insertLoadtestCardSecrets(connection, runId, seededSku.skuIdHex(), requiredUnusedInventory);
        if (inserted < requiredUnusedInventory) {
            throw new IllegalStateException("Inserted card secret inventory is insufficient: " + inserted);
        }
        return findEligibleSkuById(connection, seededSku.skuIdHex())
                .orElseThrow(() -> new IllegalStateException("Seeded loadtest SKU was not found after inventory insert."));
    }

    private static Optional<SelectedSku> findEligibleSku(Connection connection, int requiredUnusedInventory) throws SQLException {
        String sql = """
                WITH candidate AS (
                    SELECT inventory.sku_id,
                           COUNT(*)::int AS available_unused
                    FROM card_secret_inventory inventory
                    WHERE inventory.status = 'UNUSED'
                    GROUP BY inventory.sku_id
                    HAVING COUNT(*) >= ?
                )
                SELECT encode(sku.id, 'hex') AS sku_id_hex,
                       sku.sku_code,
                       sku.sku_name,
                       sku.spec_json::text AS spec_json,
                       sku.sku_image_url,
                       sku.price_yuan,
                       sku.spu_id,
                       candidate.available_unused
                FROM candidate
                INNER JOIN product_sku sku ON sku.id = candidate.sku_id
                INNER JOIN product_spu spu ON spu.id = sku.spu_id
                WHERE sku.status = 'ACTIVE'
                  AND spu.status = 'ACTIVE'
                ORDER BY candidate.available_unused DESC, sku.updated_at DESC, sku.id ASC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requiredUnusedInventory);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toSelectedSku(resultSet));
            }
        }
    }

    private static Optional<SelectedSku> findEligibleSkuById(Connection connection, String skuIdHex) throws SQLException {
        String sql = """
                SELECT encode(sku.id, 'hex') AS sku_id_hex,
                       sku.sku_code,
                       sku.sku_name,
                       sku.spec_json::text AS spec_json,
                       sku.sku_image_url,
                       sku.price_yuan,
                       sku.spu_id,
                       COUNT(inventory.id)::int AS available_unused
                FROM product_sku sku
                INNER JOIN product_spu spu ON spu.id = sku.spu_id
                LEFT JOIN card_secret_inventory inventory
                       ON inventory.sku_id = sku.id
                      AND inventory.status = 'UNUSED'
                WHERE sku.id = decode(?, 'hex')
                  AND sku.status = 'ACTIVE'
                  AND spu.status = 'ACTIVE'
                GROUP BY sku.id, sku.sku_code, sku.sku_name, sku.spec_json, sku.sku_image_url, sku.price_yuan, sku.spu_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skuIdHex);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toSelectedSku(resultSet));
            }
        }
    }

    private static SelectedSku toSelectedSku(ResultSet resultSet) throws SQLException {
        String skuIdHex = resultSet.getString("sku_id_hex");
        return new SelectedSku(
                skuIdHex,
                HybridIdCodec.hexToBase62(skuIdHex),
                resultSet.getString("sku_code"),
                resultSet.getString("sku_name"),
                resultSet.getString("spec_json"),
                resultSet.getString("sku_image_url"),
                money(resultSet.getBigDecimal("price_yuan")),
                resultSet.getLong("spu_id"),
                resultSet.getInt("available_unused")
        );
    }

    private static void upsertLoadtestCategory(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO product_category (
                    id, parent_id, name, code, level, path, sort_order, icon_urls, description, status, is_leaf, created_at, updated_at
                )
                VALUES (?, 0, 'Duplicate Callback Loadtest', 'ORDER_DUP_CB_LOADTEST', 1, ?, 0, '[]'::jsonb,
                        'duplicate callback card secret loadtest category', 'ACTIVE', true, NOW(), NOW())
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    code = EXCLUDED.code,
                    path = EXCLUDED.path,
                    status = 'ACTIVE',
                    is_leaf = true,
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, LOADTEST_CATEGORY_ID);
            statement.setString(2, "/" + LOADTEST_CATEGORY_ID + "/");
            statement.executeUpdate();
        }
    }

    private static void upsertLoadtestSpu(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO product_spu (
                    id, category_id, name, subtitle, brand_name, main_image_url, status, created_at, updated_at
                )
                VALUES (?, ?, 'Duplicate Callback Card Secret Loadtest Product',
                        'duplicate callback idempotency loadtest product', 'Loadtest', '', 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO UPDATE
                SET category_id = EXCLUDED.category_id,
                    name = EXCLUDED.name,
                    subtitle = EXCLUDED.subtitle,
                    brand_name = EXCLUDED.brand_name,
                    status = 'ACTIVE',
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, LOADTEST_SPU_ID);
            statement.setLong(2, LOADTEST_CATEGORY_ID);
            statement.executeUpdate();
        }
    }

    private static SelectedSku upsertLoadtestSku(Connection connection, int stockQuantity) throws SQLException {
        HybridSemaphoreIdWorker idWorker = new HybridSemaphoreIdWorker(1, 34);
        String skuIdHex = HybridIdCodec.toHex(idWorker.nextId());
        String sql = """
                INSERT INTO product_sku (
                    id, spu_id, sku_code, sku_name, spec_json, sku_image_url, price_yuan, original_price_yuan,
                    stock_quantity, status, created_at, updated_at
                )
                VALUES (decode(?, 'hex'), ?, ?, 'Duplicate Callback Card Secret SKU',
                        '{"loadtest":true,"scenario":"duplicate-callback-card-secret"}'::jsonb,
                        '[]', 9.90, 19.90, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (sku_code) DO UPDATE
                SET spu_id = EXCLUDED.spu_id,
                    sku_name = EXCLUDED.sku_name,
                    spec_json = EXCLUDED.spec_json,
                    sku_image_url = EXCLUDED.sku_image_url,
                    price_yuan = EXCLUDED.price_yuan,
                    original_price_yuan = EXCLUDED.original_price_yuan,
                    stock_quantity = GREATEST(product_sku.stock_quantity, EXCLUDED.stock_quantity),
                    status = 'ACTIVE',
                    updated_at = NOW()
                RETURNING encode(id, 'hex') AS sku_id_hex,
                          sku_code,
                          sku_name,
                          spec_json::text AS spec_json,
                          sku_image_url,
                          price_yuan,
                          spu_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skuIdHex);
            statement.setLong(2, LOADTEST_SPU_ID);
            statement.setString(3, LOADTEST_SKU_CODE);
            statement.setInt(4, stockQuantity);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Loadtest SKU upsert returned no row.");
                }
                String returnedSkuIdHex = resultSet.getString("sku_id_hex");
                return new SelectedSku(
                        returnedSkuIdHex,
                        HybridIdCodec.hexToBase62(returnedSkuIdHex),
                        resultSet.getString("sku_code"),
                        resultSet.getString("sku_name"),
                        resultSet.getString("spec_json"),
                        resultSet.getString("sku_image_url"),
                        money(resultSet.getBigDecimal("price_yuan")),
                        resultSet.getLong("spu_id"),
                        0
                );
            }
        }
    }

    private static int insertLoadtestCardSecrets(Connection connection,
                                                 String runId,
                                                 String skuIdHex,
                                                 int count) throws SQLException, JsonProcessingException {
        HybridSemaphoreIdWorker idWorker = new HybridSemaphoreIdWorker(1, 35);
        List<CardSecretSeedInput> cards = new ArrayList<>(count);
        for (int index = 1; index <= count; index += 1) {
            byte[] id = idWorker.nextId();
            String idHex = HybridIdCodec.toHex(id);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(id);
            String secretHash = sha256Hex("duplicate-callback-card-secret:" + runId + ":" + index + ":" + idHex);
            cards.add(new CardSecretSeedInput(
                    idHex,
                    "DUP_CB_" + abbreviate(runId, 40),
                    "LOADTEST-CIPHERTEXT-" + nonce,
                    nonce,
                    secretHash,
                    "duplicate callback card secret loadtest"
            ));
        }
        String sql = """
                WITH raw AS (
                    SELECT *
                    FROM jsonb_to_recordset(?::jsonb) AS x(
                        id_hex text,
                        batch_no text,
                        secret_ciphertext text,
                        secret_nonce text,
                        secret_hash text,
                        remark text
                    )
                ),
                inserted AS (
                    INSERT INTO card_secret_inventory (
                        id,
                        sku_id,
                        batch_no,
                        secret_ciphertext,
                        secret_nonce,
                        secret_hash,
                        secret_key_version,
                        encrypt_algorithm,
                        hash_algorithm,
                        status,
                        remark,
                        import_source,
                        created_by_admin_username,
                        created_at,
                        updated_at
                    )
                    SELECT decode(id_hex, 'hex'),
                           decode(?, 'hex'),
                           batch_no,
                           secret_ciphertext,
                           secret_nonce,
                           secret_hash,
                           'v1',
                           'AES_256_GCM',
                           'HMAC_SHA256',
                           'UNUSED',
                           remark,
                           'TEXT_INPUT',
                           'loadtest',
                           NOW(),
                           NOW()
                    FROM raw
                    ON CONFLICT (id) DO NOTHING
                    RETURNING id
                )
                SELECT COUNT(*)::int AS inserted_count
                FROM inserted
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toJson(cards));
            statement.setString(2, skuIdHex);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt("inserted_count");
            }
        }
    }

    private static List<OrderSeedInput> buildOrderRows(int orderCount,
                                                       int quantityPerOrder,
                                                       String runId,
                                                       SelectedSku sku,
                                                       long userIdStart) {
        HybridSemaphoreIdWorker idWorker = new HybridSemaphoreIdWorker(1, 36);
        List<OrderSeedInput> rows = new ArrayList<>(orderCount);
        for (int index = 1; index <= orderCount; index += 1) {
            String orderNo = HybridIdCodec.toBase62(idWorker.nextId());
            long userId = userIdStart + index - 1L;
            BigDecimal lineAmount = money(sku.priceYuan().multiply(BigDecimal.valueOf(quantityPerOrder)));
            rows.add(new OrderSeedInput(
                    orderNo,
                    userId,
                    sku.spuId(),
                    sku.skuIdHex(),
                    sku.skuCode(),
                    sku.skuName(),
                    sku.specJson(),
                    sku.skuImageUrl(),
                    quantityPerOrder,
                    sku.priceYuan(),
                    lineAmount,
                    lineAmount,
                    "loadtest-duplicate-callback-" + abbreviate(runId, 72) + "-" + orderNo,
                    "DUPCB-" + abbreviate(runId, 96) + "-" + orderNo
            ));
        }
        return rows;
    }

    private static InsertSummary insertOrdersAndItems(Connection connection,
                                                     List<OrderSeedInput> rows) throws SQLException, JsonProcessingException {
        String sql = """
                WITH raw AS (
                    SELECT *
                    FROM jsonb_to_recordset(?::jsonb) AS x(
                        order_no text,
                        user_id bigint,
                        spu_id bigint,
                        sku_id_hex text,
                        sku_code text,
                        sku_name text,
                        spec_json text,
                        sku_image_url text,
                        quantity integer,
                        sale_price_yuan numeric,
                        line_amount_yuan numeric,
                        pay_amount_yuan numeric,
                        idempotency_key text,
                        external_trade_no text
                    )
                ),
                inserted_orders AS (
                    INSERT INTO trade_order (
                        order_no,
                        user_id,
                        status,
                        total_amount_yuan,
                        discount_amount_yuan,
                        pay_amount_yuan,
                        user_coupon_id,
                        idempotency_key,
                        expire_at,
                        paid_at,
                        closing_at,
                        closing_deadline_at,
                        cancelled_at,
                        closed_at,
                        created_at,
                        updated_at,
                        version
                    )
                    SELECT order_no,
                           user_id,
                           'PENDING_PAYMENT',
                           pay_amount_yuan,
                           0,
                           pay_amount_yuan,
                           NULL,
                           idempotency_key,
                           NOW() + INTERVAL '1 day',
                           NULL,
                           NULL,
                           NULL,
                           NULL,
                           NULL,
                           NOW(),
                           NOW(),
                           1
                    FROM raw
                    ORDER BY order_no
                    ON CONFLICT (order_no) DO NOTHING
                    RETURNING order_no
                ),
                inserted_items AS (
                    INSERT INTO trade_order_item (
                        order_no,
                        user_id,
                        spu_id,
                        sku_id,
                        sku_code,
                        sku_name,
                        spec_json,
                        sku_image_url,
                        quantity,
                        sale_price_yuan,
                        line_amount_yuan,
                        is_hot_sku,
                        created_at
                    )
                    SELECT raw.order_no,
                           raw.user_id,
                           raw.spu_id,
                           decode(raw.sku_id_hex, 'hex'),
                           raw.sku_code,
                           raw.sku_name,
                           CAST(raw.spec_json AS jsonb),
                           raw.sku_image_url,
                           raw.quantity,
                           raw.sale_price_yuan,
                           raw.line_amount_yuan,
                           false,
                           NOW()
                    FROM raw
                    INNER JOIN inserted_orders inserted ON inserted.order_no = raw.order_no
                    ORDER BY raw.order_no, raw.sku_id_hex
                    ON CONFLICT (order_no, sku_id) DO NOTHING
                    RETURNING order_no
                )
                SELECT (SELECT COUNT(*)::int FROM inserted_orders) AS order_count,
                       (SELECT COUNT(*)::int FROM inserted_items) AS item_count
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toJson(rows));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new InsertSummary(
                        resultSet.getInt("order_count"),
                        resultSet.getInt("item_count")
                );
            }
        }
    }

    private static List<CallbackCsvRow> buildCallbackRows(String runId,
                                                          List<OrderSeedInput> orders,
                                                          int duplicateCallbacksPerOrder) {
        List<CallbackCsvRow> rows = new ArrayList<>(orders.size() * duplicateCallbacksPerOrder);
        for (OrderSeedInput order : orders) {
            for (int duplicateIndex = 1; duplicateIndex <= duplicateCallbacksPerOrder; duplicateIndex += 1) {
                rows.add(new CallbackCsvRow(
                        runId,
                        order.order_no(),
                        order.external_trade_no(),
                        order.pay_amount_yuan(),
                        "SIMULATED",
                        order.quantity(),
                        duplicateIndex
                ));
            }
        }
        return rows;
    }

    private static void writeCsv(List<CallbackCsvRow> rows, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("run_id,order_no,external_trade_no,paid_amount_yuan,payment_provider,expected_quantity,duplicate_index");
            writer.newLine();
            for (CallbackCsvRow row : rows) {
                writer.write(csv(row.run_id()));
                writer.write(',');
                writer.write(csv(row.order_no()));
                writer.write(',');
                writer.write(csv(row.external_trade_no()));
                writer.write(',');
                writer.write(csv(row.paid_amount_yuan()));
                writer.write(',');
                writer.write(csv(row.payment_provider()));
                writer.write(',');
                writer.write(csv(row.expected_quantity()));
                writer.write(',');
                writer.write(csv(row.duplicate_index()));
                writer.newLine();
            }
        }
    }

    private static String toJson(Object value) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(value);
    }

    private static BigDecimal money(BigDecimal value) {
        BigDecimal raw = value == null ? BigDecimal.ZERO : value;
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.contains(",") && !text.contains("\"") && !text.contains("\n") && !text.contains("\r")) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String abbreviate(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static String defaultRunId() {
        return "duplicate-callback-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(OffsetDateTime.now());
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

    private static boolean parseBoolean(String[] args, int index, boolean defaultValue, String name) {
        String value = arg(args, index, String.valueOf(defaultValue)).toLowerCase();
        if ("true".equals(value) || "1".equals(value) || "yes".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "0".equals(value) || "no".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false: " + value);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private record SelectedSku(String skuIdHex,
                               String skuId,
                               String skuCode,
                               String skuName,
                               String specJson,
                               String skuImageUrl,
                               BigDecimal priceYuan,
                               long spuId,
                               int availableUnused) {
    }

    private record CardSecretSeedInput(String id_hex,
                                       String batch_no,
                                       String secret_ciphertext,
                                       String secret_nonce,
                                       String secret_hash,
                                       String remark) {
    }

    private record OrderSeedInput(String order_no,
                                  long user_id,
                                  long spu_id,
                                  String sku_id_hex,
                                  String sku_code,
                                  String sku_name,
                                  String spec_json,
                                  String sku_image_url,
                                  int quantity,
                                  BigDecimal sale_price_yuan,
                                  BigDecimal line_amount_yuan,
                                  BigDecimal pay_amount_yuan,
                                  String idempotency_key,
                                  String external_trade_no) {
    }

    private record CallbackCsvRow(String run_id,
                                  String order_no,
                                  String external_trade_no,
                                  BigDecimal paid_amount_yuan,
                                  String payment_provider,
                                  int expected_quantity,
                                  int duplicate_index) {
    }

    private record InsertSummary(int orderCount, int itemCount) {
    }
}
