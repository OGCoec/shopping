package com.example.ShoppingSystem.quota;
import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys.AccountType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
public interface Ip2LocationQuotaService {
    public record QuotaAcquireResult(boolean allowCall, String quotaKey, long totalQuotaCount, String reason) {

            public static QuotaAcquireResult allowed(String quotaKey, long totalQuotaCount) {
                return new QuotaAcquireResult(true, quotaKey, totalQuotaCount, null);
            }

            public static QuotaAcquireResult denied(long totalQuotaCount, String reason) {
                return new QuotaAcquireResult(false, null, totalQuotaCount, reason);
            }
        }

    public record QuotaKeySnapshot(String quotaKey, long remainingQuota, long ttlSeconds) {
        }

    public record QuotaKeyListResult(long aggregateTotalQuotaCount,
                                         long realTotalQuotaCount,
                                         List<QuotaKeySnapshot> quotaKeys) {
        }

    public record QuotaKeyUpsertCommand(String apiKey,
                                            String quotaKey,
                                            long remainingQuota,
                                            long ttlSeconds) {
        }

    public record QuotaBatchUpsertResult(int upsertedCount,
                                             int oldDeletedCount,
                                             long totalQuotaCount) {
        }

    public record QuotaBatchDeleteResult(int deletedCount,
                                             long totalQuotaCount) {
        }

    public List initializeMonthlyQuota(String apiKey, long remainingQuota);

    public List initializeMonthlyQuota(String apiKey, AccountType accountType);

    public List initializeMonthlyQuota(String apiKey, long remainingQuota, AccountType accountType);

    public List decrementQuota(String quotaKey);

    public List compensateQuota(String quotaKey);

    public QuotaAcquireResult acquireQuotaForCall();

    public long getTotalQuotaCount();

    public void refreshMonthlyQuota();

    public List rebuildQuotaCount();

    public QuotaKeyListResult listQuotaKeys();

    public QuotaBatchUpsertResult batchUpsertQuotaKeys(Collection<QuotaKeyUpsertCommand> commands);

    public QuotaBatchDeleteResult batchDeleteQuotaKeys(Collection<String> quotaKeys);

    public String buildQuotaKey(String apiKey, LocalDateTime dateTime);

    public String buildQuotaKey(String apiKey, LocalDateTime dateTime, AccountType accountType);

    public long resolveQuotaAmount(AccountType accountType);

    public Duration resolveQuotaTtl(AccountType accountType);
}
