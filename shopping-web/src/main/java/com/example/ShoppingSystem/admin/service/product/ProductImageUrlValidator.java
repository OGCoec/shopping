package com.example.ShoppingSystem.admin.service.product;

import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ProductImageUrlValidator {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("javascript", "data", "vbscript", "file");
    private static final Pattern SCHEME_PATTERN = Pattern.compile("^[a-z][a-z0-9+.-]*$");

    private final ProductImageUrlSecurityProperties properties;

    public ProductImageUrlValidator(ProductImageUrlSecurityProperties properties) {
        this.properties = properties;
    }

    public String validateNullableImageUrl(String rawUrl, String label) {
        String value = normalizeText(rawUrl);
        if (value.isEmpty()) {
            return null;
        }
        validateImageUrl(value, label);
        return value;
    }

    public String validateImageUrl(String rawUrl, String label) {
        String value = normalizeText(rawUrl);
        if (value.isEmpty()) {
            return "";
        }
        if (value.indexOf('\\') >= 0 || value.startsWith("//")) {
            throw invalid(label);
        }
        String lowerValue = value.toLowerCase(Locale.ROOT);
        int colonIndex = lowerValue.indexOf(':');
        if (colonIndex > 0) {
            String candidateScheme = lowerValue.substring(0, colonIndex);
            if (SCHEME_PATTERN.matcher(candidateScheme).matches() && BLOCKED_SCHEMES.contains(candidateScheme)) {
                throw invalid(label);
            }
        }
        if (value.startsWith("/")) {
            if (isAllowedPath(value)) {
                return value;
            }
            throw invalid(label);
        }
        URI uri = parseUri(value, label);
        String scheme = normalizeText(uri.getScheme()).toLowerCase(Locale.ROOT);
        String host = normalizeText(uri.getHost()).toLowerCase(Locale.ROOT);
        if (scheme.isEmpty() || host.isEmpty()) {
            throw invalid(label);
        }
        if (BLOCKED_SCHEMES.contains(scheme)) {
            throw invalid(label);
        }
        if ("https".equals(scheme) && isAllowedHost(host)) {
            return value;
        }
        if ("http".equals(scheme) && properties.isAllowLocalHttp() && isLocalhost(host)) {
            return value;
        }
        throw invalid(label);
    }

    private boolean isAllowedPath(String value) {
        return properties.getAllowedPathPrefixes().stream()
                .map(ProductImageUrlValidator::normalizeText)
                .filter(prefix -> !prefix.isEmpty())
                .anyMatch(value::startsWith);
    }

    private boolean isAllowedHost(String host) {
        return properties.getAllowedHosts().stream()
                .map(ProductImageUrlValidator::normalizeHost)
                .filter(allowed -> !allowed.isEmpty())
                .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }

    private static String normalizeHost(String value) {
        String host = normalizeText(value).toLowerCase(Locale.ROOT);
        if (host.startsWith("http://") || host.startsWith("https://")) {
            try {
                host = normalizeText(new URI(host).getHost()).toLowerCase(Locale.ROOT);
            } catch (URISyntaxException ignored) {
                return "";
            }
        }
        return host.startsWith(".") ? host.substring(1) : host;
    }

    private static URI parseUri(String value, String label) {
        try {
            return new URI(value);
        } catch (URISyntaxException ex) {
            throw invalid(label);
        }
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host);
    }

    private static String normalizeText(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static AdminServiceException invalid(String label) {
        return new AdminServiceException(
                "ADMIN_PRODUCT_IMAGE_URL_INVALID",
                label + " image URL is not allowed.",
                HttpStatus.BAD_REQUEST);
    }
}
