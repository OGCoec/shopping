package com.example.ShoppingSystem.filter.preauth.support;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;

public final class PreAuthIpNormalizer {

    private PreAuthIpNormalizer() {
    }

    public static String normalizeIp(String rawIp) {
        if (StrUtil.isBlank(rawIp)) {
            return "";
        }
        String value = rawIp.trim().toLowerCase(Locale.ROOT);
        int commaIndex = value.indexOf(',');
        if (commaIndex >= 0) {
            value = value.substring(0, commaIndex).trim();
        }
        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']')).trim();
        }
        if (value.matches("^\\d{1,3}(\\.\\d{1,3}){3}:\\d+$")) {
            value = value.substring(0, value.lastIndexOf(':'));
        }
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length());
        }
        if (!isLikelyIpLiteral(value) || isPrivateOrLocalIp(value)) {
            return "";
        }
        return value;
    }

    private static boolean isLikelyIpLiteral(String value) {
        return StrUtil.isNotBlank(value)
                && (isLikelyIpv4(value) || value.matches("^[0-9a-f:]+$"));
    }

    private static boolean isLikelyIpv4(String value) {
        if (!value.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            return false;
        }
        String[] parts = value.split("\\.");
        for (String part : parts) {
            if (parseOctet(part) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPrivateOrLocalIp(String value) {
        if (isLikelyIpv4(value)) {
            String[] parts = value.split("\\.");
            int first = parseOctet(parts[0]);
            int second = parseOctet(parts[1]);
            return first == 10
                    || first == 127
                    || first == 0
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        return "::1".equals(value)
                || value.startsWith("fc")
                || value.startsWith("fd")
                || value.startsWith("fe80:");
    }

    private static int parseOctet(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value >= 0 && value <= 255 ? value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
