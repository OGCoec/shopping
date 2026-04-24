package com.example.ShoppingSystem;

import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionTest {

    static {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static final String JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/shopping";
    private static final String JDBC_USERNAME = "postgres";
    private static final String JDBC_PASSWORD = "123456";

    @Test
    void shouldConnectToPostgres() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USERNAME, JDBC_PASSWORD)) {
            assertNotNull(connection, "Connection should not be null");
            assertTrue(connection.isValid(2), "PostgreSQL connection should be valid");
        }
    }
}
