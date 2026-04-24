package com.example.ShoppingSystem.quota;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 手动录入 IP2Location.io 月额度的 main 方法。
 * 运行后从终端读取 API key 与剩余额度，并写入 Redis 第 3 分区。
 */
public class Ip2LocationQuotaWriteInRedisMain {

    private static final DateTimeFormatter QUOTA_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");
    private static final String QUOTA_PREFIX = "ip2location:quota:";
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
        System.out.print("请输入 API key：");
        String apiKey = reader.readLine().trim();

        System.out.print("请输入剩余额度：");
        long remainingQuota = Long.parseLong(reader.readLine().trim());

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
                    System.out.println("额度写入失败：当前正有刷新任务或其他录入操作占用锁");
                    return;
                }

                List result = quotaService.initializeMonthlyQuota(apiKey, remainingQuota);
                String quotaKey = quotaService.buildQuotaKey(apiKey, LocalDateTime.now());

                System.out.println("写入成功");
                System.out.println("Redis DB: 2");
                System.out.println("Key: " + quotaKey);
                System.out.println("Value: " + remainingQuota);
                System.out.println("CountKey: ip2location:quota:count");
                System.out.println("CountValue: " + redisTemplate.opsForValue().get("ip2location:quota:count"));
                System.out.println("LuaResult: " + result);
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

    private static String buildQuotaKey(String apiKey, LocalDateTime dateTime) {
        return QUOTA_PREFIX + dateTime.format(QUOTA_TIME_FORMATTER) + ":" + apiKey;
    }
}
