package com.example.ShoppingSystem.tools.debezium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductSpuDebeziumMonitorMain {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Properties props = debeziumProperties();
        CountDownLatch stopSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "product-spu-debezium-monitor");
            thread.setDaemon(false);
            return thread;
        });

        DebeziumEngine<ChangeEvent<String, String>> engine = DebeziumEngine.create(Json.class)
                .using(props)
                .notifying(ProductSpuDebeziumMonitorMain::handleBatch)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                engine.close();
            } catch (IOException e) {
                System.err.println("[ProductSpuCDC] close Debezium engine failed: " + e.getMessage());
            }
            executor.shutdownNow();
            stopSignal.countDown();
        }, "product-spu-debezium-monitor-shutdown"));

        System.out.println("========================================");
        System.out.println("  Debezium PostgreSQL monitor started.");
        System.out.println("  Table: public.product_spu");
        System.out.println("  It prints INSERT / UPDATE / DELETE changes.");
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println("========================================");

        executor.execute(engine);
        stopSignal.await();
    }

    private static Properties debeziumProperties() throws IOException {
        Path offsetFile = Path.of(config("shopping.debezium.offset.file",
                "SHOPPING_DEBEZIUM_OFFSET_FILE",
                "target/debezium/product-spu-offsets.dat"));
        Path offsetDir = offsetFile.toAbsolutePath().getParent();
        if (offsetDir != null) {
            Files.createDirectories(offsetDir);
        }

        Properties props = new Properties();
        props.setProperty("name", "shopping-product-spu-monitor");
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", offsetFile.toString());
        props.setProperty("offset.flush.interval.ms", "1000");

        props.setProperty("database.hostname", config("shopping.debezium.database.host",
                "SHOPPING_DB_HOST", "127.0.0.1"));
        props.setProperty("database.port", config("shopping.debezium.database.port",
                "SHOPPING_DB_PORT", "5432"));
        props.setProperty("database.user", config("shopping.debezium.database.user",
                "SHOPPING_DB_USER", "postgres"));
        props.setProperty("database.password", config("shopping.debezium.database.password",
                "SHOPPING_DB_PASSWORD", "123456"));
        props.setProperty("database.dbname", config("shopping.debezium.database.name",
                "SHOPPING_DB_NAME", "shopping"));

        props.setProperty("topic.prefix", "shopping-product-spu");
        props.setProperty("plugin.name", "pgoutput");
        props.setProperty("schema.include.list", "public");
        props.setProperty("table.include.list", "public.product_spu");
        props.setProperty("slot.name", config("shopping.debezium.slot.name",
                "SHOPPING_DEBEZIUM_SLOT_NAME", "shopping_product_spu_monitor"));
        props.setProperty("publication.name", config("shopping.debezium.publication.name",
                "SHOPPING_DEBEZIUM_PUBLICATION_NAME", "shopping_product_spu_publication"));
        props.setProperty("publication.autocreate.mode", "filtered");

        props.setProperty("snapshot.mode", "no_data");
        props.setProperty("tombstones.on.delete", "false");
        props.setProperty("include.schema.changes", "false");
        props.setProperty("key.converter.schemas.enable", "false");
        props.setProperty("value.converter.schemas.enable", "false");
        return props;
    }

    private static void handleBatch(List<ChangeEvent<String, String>> records,
                                    DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer)
            throws InterruptedException {
        for (ChangeEvent<String, String> record : records) {
            try {
                printRecord(record);
            } catch (Exception e) {
                System.err.println("[ProductSpuCDC] parse change event failed: " + e.getMessage());
                System.err.println("[ProductSpuCDC] raw value=" + record.value());
            } finally {
                committer.markProcessed(record);
            }
        }
        committer.markBatchFinished();
    }

    private static void printRecord(ChangeEvent<String, String> record) throws IOException {
        String value = record.value();
        if (value == null || value.isBlank()) {
            return;
        }

        JsonNode payload = payload(OBJECT_MAPPER.readTree(value));
        String operation = operationName(payload.path("op").asText(""));
        JsonNode before = payload.path("before");
        JsonNode after = payload.path("after");
        JsonNode row = isPresent(after) ? after : before;
        String id = isPresent(row) && row.has("id") ? row.path("id").asText() : "-";

        System.out.printf("[ProductSpuCDC] operation=%s id=%s%n", operation, id);
        if ("UPDATE".equals(operation)) {
            System.out.println("  before=" + compact(before));
            System.out.println("  after =" + compact(after));
            return;
        }
        System.out.println("  row=" + compact(row));
    }

    private static JsonNode payload(JsonNode root) {
        JsonNode payload = root.path("payload");
        return payload.isMissingNode() || payload.isNull() ? root : payload;
    }

    private static String operationName(String op) {
        return switch (op) {
            case "c" -> "INSERT";
            case "u" -> "UPDATE";
            case "d" -> "DELETE";
            case "r" -> "READ";
            default -> "UNKNOWN(" + op + ")";
        };
    }

    private static String compact(JsonNode node) throws IOException {
        if (!isPresent(node)) {
            return "null";
        }
        return OBJECT_MAPPER.writeValueAsString(node);
    }

    private static boolean isPresent(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private static String config(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }
}
