package com.example.ShoppingSystem.admin.service.ip2location;

import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminIp2LocationBinLookupService {

    private static final String CODE_PATTERN_INVALID = "ADMIN_IP2LOCATION_PATTERN_INVALID";
    private static final String CODE_CANDIDATES_TOO_MANY = "ADMIN_IP2LOCATION_CANDIDATES_TOO_MANY";
    private static final String CODE_BIN_UNAVAILABLE = "ADMIN_IP2LOCATION_BIN_UNAVAILABLE";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final long MAX_CANDIDATE_COUNT = 65_536L;

    private final Object initLock = new Object();
    private final Object queryLock = new Object();
    private volatile IP2Location client;

    @Value("${register.ip-country-cache.bin-enabled:true}")
    private boolean enabled;

    @Value("${register.ip-country-cache.bin-path:IP2LOCATION-LITE-DB11.IPV6.BIN}")
    private String binPath;

    public AdminIp2LocationBinLookupResponse wildcardLookup(AdminIp2LocationBinLookupRequest request) {
        LookupCriteria criteria = normalizeRequest(request);
        IpPattern parsedPattern = parseIpPattern(criteria.ipPattern());
        long candidateCount = parsedPattern.candidateCount();
        if (candidateCount > MAX_CANDIDATE_COUNT) {
            throw new AdminServiceException(
                    CODE_CANDIDATES_TOO_MANY,
                    "候选 IP 过多，请缩小 IP 范围。",
                    HttpStatus.BAD_REQUEST);
        }

        IP2Location ip2Location = ensureClient();
        List<AdminIp2LocationBinLookupItemResponse> matchedItems = new ArrayList<>();
        List<AdminIp2LocationBinLookupItemResponse> unmatchedItems = new ArrayList<>();
        QueryCounter counter = new QueryCounter(candidateCount);

        int[] octets = parsedPattern.octets().clone();
        queryCandidates(ip2Location, octets, 0, criteria, matchedItems, unmatchedItems, counter);
        matchedItems.sort(Comparator.comparingLong(item -> ipv4ToLong(item.ip())));
        unmatchedItems.sort(Comparator.comparingLong(item -> ipv4ToLong(item.ip())));

        List<AdminIp2LocationBinLookupItemResponse> resultSource = new ArrayList<>(matchedItems);
        if (criteria.includeUnmatched()) {
            resultSource.addAll(unmatchedItems);
        }

        long offset = (long) (criteria.page() - 1) * criteria.pageSize();
        int fromIndex = offset >= resultSource.size() ? resultSource.size() : (int) offset;
        int toIndex = Math.min(fromIndex + criteria.pageSize(), resultSource.size());
        List<AdminIp2LocationBinLookupItemResponse> pageItems = fromIndex >= toIndex
                ? Collections.emptyList()
                : resultSource.subList(fromIndex, toIndex);

        return new AdminIp2LocationBinLookupResponse(
                parsedPattern.normalizedPattern(),
                candidateCount,
                counter.queried(),
                matchedItems.size(),
                unmatchedItems.size(),
                criteria.page(),
                criteria.pageSize(),
                resultSource.size(),
                toIndex < resultSource.size(),
                List.copyOf(pageItems));
    }

    private LookupCriteria normalizeRequest(AdminIp2LocationBinLookupRequest request) {
        if (request == null) {
            throw invalidPattern("IP 模式不能为空。");
        }
        String ipPattern = normalizeText(request.ipPattern());
        if (ipPattern == null) {
            throw invalidPattern("IP 模式不能为空。");
        }

        int page = AdminPaginationValidator.normalizePage(request.page());
        int rawPageSize = AdminPaginationValidator.normalizePageSize(request.pageSize(), DEFAULT_PAGE_SIZE);
        int pageSize = Math.min(rawPageSize, MAX_PAGE_SIZE);
        return new LookupCriteria(
                ipPattern,
                normalizeCountryCode(request.countryCode()),
                normalizeText(request.region()),
                normalizeText(request.city()),
                Boolean.TRUE.equals(request.includeUnmatched()),
                page,
                pageSize);
    }

    private IpPattern parseIpPattern(String pattern) {
        String[] parts = pattern.split("\\.", -1);
        if (parts.length != 4) {
            throw invalidPattern("IPv4 模式必须是四段格式。");
        }
        int[] octets = new int[4];
        String[] normalizedParts = new String[4];
        long candidateCount = 1L;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if ("*".equals(part)) {
                octets[i] = -1;
                normalizedParts[i] = "*";
                candidateCount *= 256L;
                continue;
            }
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    throw invalidPattern("IPv4 每段必须在 0-255 之间。");
                }
                octets[i] = value;
                normalizedParts[i] = Integer.toString(value);
            } catch (NumberFormatException e) {
                throw invalidPattern("IPv4 每段必须是数字或 *。");
            }
        }
        return new IpPattern(octets, String.join(".", normalizedParts), candidateCount);
    }

    private void queryCandidates(IP2Location ip2Location,
                                 int[] octets,
                                 int index,
                                 LookupCriteria criteria,
                                 List<AdminIp2LocationBinLookupItemResponse> matchedItems,
                                 List<AdminIp2LocationBinLookupItemResponse> unmatchedItems,
                                 QueryCounter counter) {
        if (index == octets.length) {
            AdminIp2LocationBinLookupItemResponse item = queryOne(ip2Location, octets, criteria);
            counter.incrementQueried();
            if (item.matched()) {
                matchedItems.add(item);
            } else {
                unmatchedItems.add(item);
            }
            return;
        }

        if (octets[index] == -1) {
            for (int value = 0; value <= 255; value++) {
                octets[index] = value;
                queryCandidates(ip2Location, octets, index + 1, criteria, matchedItems, unmatchedItems, counter);
            }
            octets[index] = -1;
            return;
        }

        queryCandidates(ip2Location, octets, index + 1, criteria, matchedItems, unmatchedItems, counter);
    }

    private AdminIp2LocationBinLookupItemResponse queryOne(IP2Location ip2Location, int[] octets, LookupCriteria criteria) {
        String ip = octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
        try {
            IPResult result;
            synchronized (queryLock) {
                result = ip2Location.IPQuery(ip);
            }
            return toItem(ip, result, criteria);
        } catch (Exception e) {
            return new AdminIp2LocationBinLookupItemResponse(
                    ip,
                    false,
                    "ERROR",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of("status"));
        }
    }

    private AdminIp2LocationBinLookupItemResponse toItem(String ip, IPResult result, LookupCriteria criteria) {
        String status = normalizeText(result == null ? null : result.getStatus());
        String countryCode = normalizeCountryCode(result == null ? null : result.getCountryShort());
        String countryName = normalizeNullableText(result == null ? null : result.getCountryLong());
        String region = normalizeNullableText(result == null ? null : result.getRegion());
        String city = normalizeNullableText(result == null ? null : result.getCity());
        String district = normalizeNullableText(result == null ? null : result.getDistrict());

        List<String> mismatchReasons = mismatchReasons(status, countryCode, region, city, criteria);
        boolean matched = mismatchReasons.isEmpty();

        return new AdminIp2LocationBinLookupItemResponse(
                ip,
                matched,
                status == null ? "ERROR" : status,
                countryCode,
                countryName,
                region,
                city,
                district,
                result == null ? null : toCoordinate(result.getLatitude()),
                result == null ? null : toCoordinate(result.getLongitude()),
                mismatchReasons);
    }

    private List<String> mismatchReasons(String status,
                                         String countryCode,
                                         String region,
                                         String city,
                                         LookupCriteria criteria) {
        List<String> reasons = new ArrayList<>();
        if (!"OK".equalsIgnoreCase(status == null ? "" : status)) {
            reasons.add("status");
            return reasons;
        }
        if (criteria.countryCode() != null && !criteria.countryCode().equalsIgnoreCase(nullToEmpty(countryCode))) {
            reasons.add("country");
        }
        if (criteria.region() != null && !criteria.region().equalsIgnoreCase(nullToEmpty(region))) {
            reasons.add("region");
        }
        if (criteria.city() != null && !criteria.city().equalsIgnoreCase(nullToEmpty(city))) {
            reasons.add("city");
        }
        return List.copyOf(reasons);
    }

    private IP2Location ensureClient() {
        if (!enabled) {
            throw new AdminServiceException(CODE_BIN_UNAVAILABLE, "本地 IP2Location BIN 查询未启用。", HttpStatus.SERVICE_UNAVAILABLE);
        }

        IP2Location current = client;
        if (current != null) {
            return current;
        }

        synchronized (initLock) {
            if (client != null) {
                return client;
            }
            Path resolved = resolveBinPath(binPath);
            if (resolved == null || !Files.isRegularFile(resolved)) {
                throw new AdminServiceException(CODE_BIN_UNAVAILABLE, "本地 IP2Location BIN 文件不存在。", HttpStatus.SERVICE_UNAVAILABLE);
            }
            try {
                IP2Location created = new IP2Location();
                created.Open(resolved.toString(), true);
                client = created;
                return created;
            } catch (Exception e) {
                throw new AdminServiceException(CODE_BIN_UNAVAILABLE, "本地 IP2Location BIN 加载失败。", HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
    }

    private Path resolveBinPath(String configuredPath) {
        if (normalizeText(configuredPath) == null) {
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

    private AdminServiceException invalidPattern(String message) {
        return new AdminServiceException(CODE_PATTERN_INVALID, message, HttpStatus.BAD_REQUEST);
    }

    private BigDecimal toCoordinate(float value) {
        return BigDecimal.valueOf(value);
    }

    private String normalizeCountryCode(String value) {
        String normalized = normalizeText(value);
        if (normalized == null || "-".equals(normalized)) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("UK".equals(upper)) {
            return "GB";
        }
        if ("USA".equals(upper)) {
            return "US";
        }
        return upper.matches("[A-Z]{2}") ? upper : null;
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeText(value);
        if (normalized == null
                || "-".equals(normalized)
                || "N/A".equalsIgnoreCase(normalized)
                || normalized.replace('_', ' ').toLowerCase(Locale.ROOT).contains("not supported")) {
            return null;
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.", -1);
        long value = 0L;
        for (String part : parts) {
            value = (value << 8) + Integer.parseInt(part);
        }
        return value;
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

    private record LookupCriteria(String ipPattern,
                                  String countryCode,
                                  String region,
                                  String city,
                                  boolean includeUnmatched,
                                  int page,
                                  int pageSize) {
    }

    private record IpPattern(int[] octets, String normalizedPattern, long candidateCount) {
    }

    private static final class QueryCounter {
        private final long candidateCount;
        private long queried;

        private QueryCounter(long candidateCount) {
            this.candidateCount = candidateCount;
        }

        private void incrementQueried() {
            if (queried < candidateCount) {
                queried++;
            }
        }

        private long queried() {
            return queried;
        }
    }
}
