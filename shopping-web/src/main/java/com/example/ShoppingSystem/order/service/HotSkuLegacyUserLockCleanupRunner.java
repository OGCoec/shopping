package com.example.ShoppingSystem.order.service;

import com.example.ShoppingSystem.order.redis.OrderRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HotSkuLegacyUserLockCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HotSkuLegacyUserLockCleanupRunner.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> cleanupScript;
    private final boolean enabled;
    private final int scanCount;

    public HotSkuLegacyUserLockCleanupRunner(StringRedisTemplate stringRedisTemplate,
                                             @Value("${app.order.hot-sku.cleanup-legacy-user-locks-on-startup:true}") boolean enabled,
                                             @Value("${app.order.hot-sku.legacy-user-lock-cleanup-scan-count:500}") int scanCount) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.enabled = enabled;
        this.scanCount = scanCount;
        this.cleanupScript = listRedisScript("lua/hot_sku_legacy_user_cleanup.lua");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            List<?> result = stringRedisTemplate.execute(
                    cleanupScript,
                    List.of(),
                    OrderRedisKeys.hotSkuLegacyUserKeyPattern(),
                    String.valueOf(Math.max(1, scanCount))
            );
            log.info("[Order] hot SKU legacy user locks cleanup finished, deleted={}", resultLong(result, 0));
        } catch (Exception e) {
            throw new IllegalStateException("Hot SKU legacy user lock cleanup failed.", e);
        }
    }

    private long resultLong(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return 0L;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private DefaultRedisScript<List> listRedisScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        script.setResultType(List.class);
        return script;
    }
}
