package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

public final class OrderRowMapper {

    private OrderRowMapper() {
    }

    public static Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(toSnakeCase(key));
    }

    public static String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static Long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }

    public static int intValue(Map<String, Object> row, String key, int defaultValue) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(text);
    }

    public static boolean boolValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    public static BigDecimal decimal(Map<String, Object> row, String key) {
        BigDecimal value = nullableDecimal(row, key);
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal nullableDecimal(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    public static OffsetDateTime offsetDateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? null : OffsetDateTime.parse(text);
    }

    public static byte[] idBytes(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return HybridIdCodec.fromHex(text);
    }

    public static String idText(Map<String, Object> row, String key) {
        return HybridIdCodec.toBase62FromDatabaseValue(value(row, key));
    }

    private static String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index += 1) {
            char ch = value.charAt(index);
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
