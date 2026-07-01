package com.example.ShoppingSystem.admin.service.auth;
import jakarta.servlet.http.HttpServletRequest;
public interface AdminLoginLockService {
    public record LockStatus(boolean locked, long retryAfterMs) {
            public static LockStatus open() {
                return new LockStatus(false, 0L);
            }

            public static LockStatus locked(long retryAfterMs) {
                return new LockStatus(true, Math.max(1L, retryAfterMs));
            }
        }

    public record FailureStatus(boolean locked,
                                    long retryAfterMs,
                                    int failureCount,
                                    long failureWindowMs) {
            public static FailureStatus locked(long retryAfterMs, int failureCount) {
                return new FailureStatus(true, Math.max(1L, retryAfterMs), Math.max(0, failureCount), 0L);
            }

            public static FailureStatus failed(int failureCount, long failureWindowMs) {
                return new FailureStatus(false, 0L, Math.max(0, failureCount), Math.max(0L, failureWindowMs));
            }
        }

    public LockStatus checkLocked(String identifier);

    public FailureStatus recordFailure(String identifier, HttpServletRequest request);

    public void clearFailures(String identifier);
}
