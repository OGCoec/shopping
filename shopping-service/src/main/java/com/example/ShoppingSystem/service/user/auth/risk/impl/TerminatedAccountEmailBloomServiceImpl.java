package com.example.ShoppingSystem.service.user.auth.risk.impl;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.config.TerminatedAccountEmailCountingBloomProperties;
import com.example.ShoppingSystem.mapper.UserRiskAccountTerminationMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import com.example.ShoppingSystem.service.user.auth.risk.TerminatedAccountEmailBloomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

@Service
public class TerminatedAccountEmailBloomServiceImpl implements TerminatedAccountEmailBloomService {

    private static final Logger log = LoggerFactory.getLogger(TerminatedAccountEmailBloomServiceImpl.class);

    private final CountingBloomFilter countingBloomFilter;
    private final UserRiskAccountTerminationMapper userRiskAccountTerminationMapper;
    private final TerminatedAccountEmailCountingBloomProperties properties;
    private final Executor executor;
    private volatile boolean bloomReady;

    public TerminatedAccountEmailBloomServiceImpl(CountingBloomFilter countingBloomFilter,
                                                  UserRiskAccountTerminationMapper userRiskAccountTerminationMapper,
                                                  TerminatedAccountEmailCountingBloomProperties properties,
                                                  @Qualifier("terminatedAccountEmailCountingBloomExecutor") Executor executor) {
        this.countingBloomFilter = countingBloomFilter;
        this.userRiskAccountTerminationMapper = userRiskAccountTerminationMapper;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public void rebuildFromDatabase() {
        if (!properties.isEnabled()) {
            log.info("Terminated account email counting bloom initialization disabled.");
            return;
        }
        int safeCapacity = Math.max(200, properties.getCapacity());
        int safeHashCount = Math.max(4, Math.min(25, properties.getHashCount()));
        int safeCounterBytes = properties.getCounterBytes() == 2 ? 2 : 1;
        int safePageSize = Math.max(100, properties.getPageSize());

        long start = System.currentTimeMillis();
        bloomReady = false;
        countingBloomFilter.reinit(properties.getKey(), safeCapacity, safeHashCount, safeCounterBytes);

        long totalRows = userRiskAccountTerminationMapper.countTerminatedEmailHashes();
        long offset = 0L;
        long loadedRows = 0L;
        while (true) {
            List<String> page = userRiskAccountTerminationMapper.listTerminatedEmailHashes(safePageSize, offset);
            if (page == null || page.isEmpty()) {
                break;
            }
            List<String> hashes = normalizeHashes(page);
            loadedRows += countingBloomFilter.addAllItems(properties.getKey(), hashes);
            offset += page.size();
        }

        log.info("Terminated account email counting bloom initialized, dbRows={}, loadedRows={}, capacity={}, hashCount={}, counterBytes={}, elapsedMs={}",
                totalRows, loadedRows, safeCapacity, safeHashCount, safeCounterBytes, System.currentTimeMillis() - start);
        bloomReady = true;
    }

    @Override
    public TerminatedAccountEmailLookupResult lookupEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (StrUtil.isBlank(normalizedEmail)) {
            return new TerminatedAccountEmailLookupResult(false, true, false, false, "", "EMAIL_BLANK");
        }
        return lookupEmailHash(sha256(normalizedEmail));
    }

    @Override
    public boolean isTerminatedEmail(String email) {
        return lookupEmail(email).terminated();
    }

    @Override
    public void addTerminatedEmailHashAsync(String emailHash) {
        String normalizedHash = normalizeHash(emailHash);
        if (!properties.isEnabled() || StrUtil.isBlank(normalizedHash)) {
            return;
        }
        executor.execute(() -> addTerminatedEmailHash(normalizedHash));
    }

    private TerminatedAccountEmailLookupResult lookupEmailHash(String emailHash) {
        String normalizedHash = normalizeHash(emailHash);
        if (StrUtil.isBlank(normalizedHash)) {
            return new TerminatedAccountEmailLookupResult(false, true, false, false, "", "EMAIL_HASH_BLANK");
        }
        if (!properties.isEnabled()) {
            return confirmByDatabase(normalizedHash, false, false, "BLOOM_DISABLED_DB_CONFIRM");
        }
        if (!bloomReady) {
            return confirmByDatabase(normalizedHash, false, false, "BLOOM_NOT_READY_DB_CONFIRM");
        }
        try {
            boolean bloomHit = Boolean.TRUE.equals(countingBloomFilter.exists(properties.getKey(), normalizedHash));
            if (!bloomHit) {
                return new TerminatedAccountEmailLookupResult(false, true, false, false, normalizedHash, "BLOOM_MISS");
            }
            return confirmByDatabase(normalizedHash, true, true, "BLOOM_HIT_DB_CONFIRM");
        } catch (RuntimeException e) {
            log.warn("Terminated account email counting bloom lookup failed, degrading to DB lookup, emailHash={}, reason={}",
                    normalizedHash, e.getMessage());
            return confirmByDatabase(normalizedHash, false, true, "BLOOM_UNAVAILABLE_DB_CONFIRM");
        }
    }

    private TerminatedAccountEmailLookupResult confirmByDatabase(String emailHash,
                                                                 boolean bloomAvailable,
                                                                 boolean bloomHit,
                                                                 String reasonCode) {
        try {
            boolean dbConfirmed = userRiskAccountTerminationMapper.existsByEmailHash(emailHash);
            return new TerminatedAccountEmailLookupResult(
                    dbConfirmed,
                    bloomAvailable,
                    bloomHit,
                    dbConfirmed,
                    emailHash,
                    dbConfirmed ? "TERMINATED_DB_CONFIRMED" : reasonCode
            );
        } catch (RuntimeException e) {
            log.warn("Terminated account email DB confirmation failed, emailHash={}, reason={}",
                    emailHash, e.getMessage());
            return new TerminatedAccountEmailLookupResult(true, bloomAvailable, bloomHit, false, emailHash, "TERMINATION_DB_LOOKUP_FAILED");
        }
    }

    private void addTerminatedEmailHash(String emailHash) {
        try {
            countingBloomFilter.add(properties.getKey(), emailHash);
        } catch (RuntimeException e) {
            log.warn("Terminated account email counting bloom add failed, emailHash={}, reason={}",
                    emailHash, e.getMessage());
        }
    }

    private List<String> normalizeHashes(List<String> hashes) {
        List<String> normalizedHashes = new ArrayList<>(hashes.size());
        for (String hash : hashes) {
            String normalizedHash = normalizeHash(hash);
            if (StrUtil.isNotBlank(normalizedHash)) {
                normalizedHashes.add(normalizedHash);
            }
        }
        return normalizedHashes;
    }

    private String normalizeEmail(String email) {
        String normalized = StrUtil.blankToDefault(email, "").trim();
        return normalized.isEmpty() ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeHash(String emailHash) {
        String normalized = StrUtil.blankToDefault(emailHash, "").trim();
        if (normalized.length() != 64) {
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", e);
        }
    }
}
