package com.example.ShoppingSystem.quota;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Ip2LocationQuotaLuaScriptTest {

    @Test
    void rebuildScriptIgnoresNonStringKeysUnderSamePrefix() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName("127.0.0.1");
        configuration.setPort(6380);
        configuration.setDatabase(2);
        configuration.setPassword(RedisPassword.of("123456"));

        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);
        String testPrefix = "test:ip2location:quota:";
        String totalKey = testPrefix + "count";
        String quotaKey = testPrefix + "2026-04-21-10:00:demo";
        String lockKey = testPrefix + "refresh:lock";

        DefaultRedisScript<List> rebuildScript = new DefaultRedisScript<>();
        rebuildScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ip2location_quota_rebuild.lua")));
        rebuildScript.setResultType(List.class);

        try {
            redisTemplate.delete(redisTemplate.keys(testPrefix + "*"));
            redisTemplate.opsForValue().set(quotaKey, "50000");
            redisTemplate.opsForHash().put(lockKey, "thread", "scheduling-1");

            List result = assertDoesNotThrow(
                    () -> redisTemplate.execute(rebuildScript, List.of(totalKey), testPrefix),
                    "rebuild script should skip non-string keys under the same prefix"
            );

            assertEquals("50000", redisTemplate.opsForValue().get(totalKey));
            assertEquals("50000", result.get(1));
        } finally {
            redisTemplate.delete(redisTemplate.keys(testPrefix + "*"));
            factory.destroy();
        }
    }
}
