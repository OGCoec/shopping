package com.example.ShoppingSystem.quota;

import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Local IP2Location BIN geo lookup service.
 */
@Service
public class Ip2LocationBinCountryService {

    private static final Logger log = LoggerFactory.getLogger(Ip2LocationBinCountryService.class);

    private final Object initLock = new Object();
    private volatile IP2Location client;

    @Value("${register.ip-country-cache.bin-enabled:true}")
    private boolean enabled;

    @Value("${register.ip-country-cache.bin-path:IP2LOCATION-LITE-DB11.IPV6.BIN}")
    private String binPath;

    public String queryCountryCode(String ip) {
        IpGeoSnapshot snapshot = queryGeo(ip);
        return snapshot == null ? null : snapshot.country();
    }

    public IpGeoSnapshot queryGeo(String ip) {
        if (!enabled || isBlank(ip)) {
            return null;
        }
        try {
            IP2Location ip2Location = ensureClient();
            if (ip2Location == null) {
                return null;
            }
            IPResult result = ip2Location.IPQuery(ip.trim());
            if (result == null) {
                return null;
            }
            if (!"OK".equalsIgnoreCase(safeText(result.getStatus()))) {
                log.debug("BIN geo query missed, ip={}, status={}", ip, result.getStatus());
                return null;
            }
            IpGeoSnapshot snapshot = new IpGeoSnapshot(
                    normalizeCountryCode(result.getCountryShort()),
                    normalizeNullableText(result.getRegion()),
                    normalizeNullableText(result.getCity()),
                    toCoordinate(result.getLatitude()),
                    toCoordinate(result.getLongitude())
            );
            return snapshot.hasAnyGeo() ? snapshot : null;
        } catch (Exception e) {
            log.warn("BIN geo query failed, ip={}, reason={}", ip, e.getMessage());
            return null;
        }
    }

    private IP2Location ensureClient() {
        IP2Location current = client;
        if (current != null) {
            return current;
        }

        synchronized (initLock) {
            if (client != null) {
                return client;
            }
            Path resolved = resolveBinPath(binPath);
            if (resolved == null || !Files.exists(resolved)) {
                log.warn("BIN file not found, skip BIN geo query, configuredPath={}, resolvedPath={}", binPath, resolved);
                return null;
            }
            try {
                IP2Location created = new IP2Location();
                created.Open(resolved.toString(), true);
                client = created;
                log.info("BIN loaded, path={}", resolved);
                return created;
            } catch (Exception e) {
                log.warn("BIN load failed, path={}, reason={}", resolved, e.getMessage());
                return null;
            }
        }
    }

    private Path resolveBinPath(String configuredPath) {
        if (isBlank(configuredPath)) {
            return null;
        }
        try {
            Path configured = Paths.get(configuredPath.trim());
            if (configured.isAbsolute()) {
                return configured.normalize();
            }
            Path userDir = Paths.get(System.getProperty("user.dir", "."));
            Path byUserDir = userDir.resolve(configured).normalize();
            if (Files.exists(byUserDir)) {
                return byUserDir;
            }
            return configured.toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeCountryCode(String countryCode) {
        if (isBlank(countryCode)) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || "-".equals(normalized)
                || "N/A".equalsIgnoreCase(normalized)
                || normalized.toLowerCase(Locale.ROOT).contains("not supported")) {
            return null;
        }
        return normalized;
    }

    private BigDecimal toCoordinate(float value) {
        return BigDecimal.valueOf(value);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @PreDestroy
    public void close() {
        IP2Location toClose = client;
        if (toClose == null) {
            return;
        }
        try {
            toClose.Close();
        } catch (Exception ignored) {
        } finally {
            client = null;
        }
    }
}
