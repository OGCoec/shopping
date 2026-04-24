package com.example.ShoppingSystem.quota;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * IP2Location.io 月额度刷新定时任务。
 */
@Component
public class Ip2LocationQuotaRefreshScheduler {

    private static final String REFRESH_LOCK_KEY = "ip2location:quota-refresh:lock";

    private final Ip2LocationQuotaService ip2LocationQuotaService;
    private final RedissonClient redissonClient;

    public Ip2LocationQuotaRefreshScheduler(Ip2LocationQuotaService ip2LocationQuotaService,
                                            RedissonClient redissonClient) {
        this.ip2LocationQuotaService = ip2LocationQuotaService;
        this.redissonClient = redissonClient;
    }

    /**
     * 每 30 分钟扫描 Redis 第 3 分区中以额度前缀开头的键，跨月时自动刷新为 50000。
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void refreshQuotaEveryThirtyMinutes() {
        RLock lock = redissonClient.getLock(REFRESH_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!locked) {
                return;
            }
            ip2LocationQuotaService.refreshMonthlyQuota();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
