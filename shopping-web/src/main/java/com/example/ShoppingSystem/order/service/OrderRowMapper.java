package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

final class OrderRowMapper {

    private OrderRowMapper() {
    }

    static Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(toSnakeCase(key));
    }

    static String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    static Long longValue(Map<String, Object> row, String key) {
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

    static int intValue(Map<String, Object> row, String key, int defaultValue) {
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

    static boolean boolValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    static BigDecimal decimal(Map<String, Object> row, String key) {
        BigDecimal value = nullableDecimal(row, key);
        return value == null ? BigDecimal.ZERO : value;
    }

    static BigDecimal nullableDecimal(Map<String, Object> row, String key) {
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

    static OffsetDateTime offsetDateTime(Map<String, Object> row, String key) {
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

    static byte[] idBytes(Map<String, Object> row, String key) {
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

    static String idText(Map<String, Object> row, String key) {
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
