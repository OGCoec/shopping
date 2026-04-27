package com.example.ShoppingSystem.tools.ip2location.quota;

import com.example.ShoppingSystem.redisdata.Ip2LocationQuotaRedisKeys.AccountType;
import com.example.ShoppingSystem.quota.Ip2LocationQuotaService;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manual writer for IP2Location quota keys in Redis DB 2.
 */
public class Ip2LocationQuotaWriteInRedisMain {

    private static final String REFRESH_LOCK_KEY = "ip2location:quota-refresh:lock";

    static {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.print("Enter API key: ");
        String apiKey = reader.readLine().trim();

        System.out.print("Enter account type (FREE/STARTER/PLUS/SECURITY): ");
        AccountType accountType = AccountType.parse(reader.readLine().trim());
        long defaultQuota = accountType.defaultMonthlyQuota();

        System.out.print("Enter remaining quota (press Enter for default " + defaultQuota + "): ");
        String remainingQuotaInput = reader.readLine();
        long remainingQuota = remainingQuotaInput == null || remainingQuotaInput.isBlank()
                ? defaultQuota
                : Long.parseLong(remainingQuotaInput.trim());

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName("127.0.0.1");
        configuration.setPort(6380);
        configuration.setDatabase(2);
        configuration.setPassword(RedisPassword.of("123456"));

        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();

        Config redissonConfig = new Config();
        redissonConfig.useSingleServer()
                .setAddress("redis://127.0.0.1:6380")
                .setPassword("123456")
                .setDatabase(2);
        RedissonClient redissonClient = Redisson.create(redissonConfig);

        try {
            StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
            Ip2LocationQuotaService quotaService = new Ip2LocationQuotaService(redisTemplate);
            RLock lock = redissonClient.getLock(REFRESH_LOCK_KEY);

            boolean locked = false;
            try {
                locked = lock.tryLock(0, 1, TimeUnit.MINUTES);
                if (!locked) {
                    System.out.println("Quota write failed: another quota maintenance operation is running.");
                    return;
                }

                List upsertResult = quotaService.initializeMonthlyQuota(apiKey, remainingQuota, accountType);
                List rebuildResult = quotaService.rebuildQuotaCount();
                String quotaKey = quotaService.buildQuotaKey(apiKey, LocalDateTime.now(), accountType);
                Duration ttl = quotaService.resolveQuotaTtl(accountType);

                System.out.println("Quota key written successfully.");
                System.out.println("Redis DB: 2");
                System.out.println("Key: " + quotaKey);
                System.out.println("Value: " + remainingQuota);
                System.out.println("AccountType: " + accountType);
                System.out.println("DefaultPlanQuota: " + defaultQuota);
                System.out.println("TTL: " + (ttl == null ? "PERSIST" : ttl.toDays() + " days"));
                System.out.println("CountKey: ip2location:quota:count");
                System.out.println("CountValue: " + redisTemplate.opsForValue().get("ip2location:quota:count"));
                System.out.println("UpsertLuaResult: " + upsertResult);
                System.out.println("RebuildLuaResult: " + rebuildResult);
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } finally {
            redissonClient.shutdown();
            factory.destroy();
        }
    }
}
