package com.example.ShoppingSystem.Utils.Lock;

import cn.hutool.core.util.IdUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的分布式锁实现
 * 满足：
 * 1. 获取锁时存入唯一的线程标识（48位 NanoID 前缀 + ThreadID）
 * 2. 释放锁时校验标识，并采用 Lua 脚本保证释放操作的原子性
 */
@Data
@AllArgsConstructor
public class RedisAsyncLock implements DistributedLock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    // JVM 级别的随机前缀，保证多实例唯一，采用 Hutool 48 位 NanoID 以增强安全性
    private static final String ID_PREFIX = IdUtil.nanoId(48) + "-";

    // 初始化 Lua 脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 从 classpath 加载脚本文件
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("com/example/orderfooddeliverysystem/Utils/Lock/Unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取当前线程唯一标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 调用 Lua 脚本实现原子释放锁
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
