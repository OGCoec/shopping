package com.example.ShoppingSystem.Utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.ShoppingSystem.Utils.redis.RedisData;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@Component
// 逻辑过期
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 准备一个线程池，专门负责后台偷偷去 MySQL 拿新数据
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(6);

    // 💡 进阶细节：为不同的 Key 维护独立的信号量，避免商品 A 的重建阻塞商品 B
    private final ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 普通缓存放入，并设置物理过期时间 (TTL)
     * 适用于验证码、Token、普通无高并发风险的数据
     *
     * @param key   Redis 键
     * @param value 业务实体对象
     * @param time  过期时间
     * @param unit  时间单位
     * @param <T>   泛型，杜绝 Object
     */
    public <T> void setWithExpire(String key, T value, Long time, TimeUnit unit) {
        // 1. 将传入的泛型对象序列化为纯净的 JSON 字符串（无 @class 污染）
        String jsonStr = JSONUtil.toJsonStr(value);

        // 2. 调用原生 StringRedisTemplate 存入数据，并让 Redis 底层自动管理它的生死
        stringRedisTemplate.opsForValue().set(key, jsonStr, time, unit);
    }

    /**
     * 写入带有逻辑过期时间的数据 (供后台数据预热使用)
     */
    public <T> void setWithLogicalExpire(String key, T value, Long time, TimeUnit unit) {
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(value); // 泛型 T 天然是 Object 的子类，这里可以直接 set 进去
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 物理上永久存活，不设置真正过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 核心防御逻辑：获取数据，若逻辑过期则通过信号量异步重建
     *
     * @param keyPrefix  Redis键前缀
     * @param id         查询的ID
     * @param type       返回对象的真实类型Class
     * @param dbFallback 兜底的查库函数 (比如 shopMapper::selectById)
     * @param time       重建后的逻辑存活时间
     * @param unit       时间单位
     * @return 返回真实的业务对象
     */
    public <R, ID> R getWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        // 1. 判断缓存是否命中
        // ⚠️ 注意：逻辑过期的前提是数据已经提前【预热】到 Redis 中。
        // 如果这里查不到，说明根本不是热点数据，直接返回 null 即可
        if (StrUtil.isBlank(jsonStr)) {
            return null;
        }

        // 2. 命中，将纯净 JSON 反序列化为我们的包装类
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        // 将内部的 data 强转回真正的泛型对象 (比如 Shop)
        R resultObj = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 3. 判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 🟢 未过期：直接返回新鲜数据
            return resultObj;
        }

        // ... 前面的逻辑不变 (判断过期) ...

        // 4. 🔴 已过期：触发缓存重建机制
        Semaphore semaphore = semaphoreMap.computeIfAbsent(key, k -> new Semaphore(1));
        // Semaphore semaphore = semaphoreMap.computeIfAbsent(key, new Function<String,
        // Semaphore>() {
        // @Override
        // public Semaphore apply(String k) {
        // // 当 map 中找不到这个 key 时，才会执行这段代码来生成一个新的锁
        // return new Semaphore(1);
        // }
        // });

        // 5. 尝试获取通行证
        if (semaphore.tryAcquire()) {
            log.info("获得信号量，准备开启异步线程重建缓存，Key: {}", key);

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // ==========================================
                    // 🚀 终极补丁：DoubleCheck (双重检查)
                    // ==========================================
                    // 拿到锁之后，第一件事是再去 Redis 里看一眼！
                    // 因为在你排队/发呆的这段时间，可能已经有其他大神把数据更新了
                    String checkJson = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(checkJson)) {
                        RedisData checkData = JSONUtil.toBean(checkJson, RedisData.class);
                        if (checkData.getExpireTime().isAfter(LocalDateTime.now())) {
                            log.info("DoubleCheck 触发：数据已被其他线程更新，取消本次数据库查询");
                            return; // 🟢 极其优雅地直接退出，绝不打扰 MySQL！
                        }
                    }
                    // ==========================================

                    // 5.1 确定还是过期的，这才执行外部传进来的查库逻辑
                    R newObj = dbFallback.apply(id);
                    // 5.2 将新数据封装好并写回 Redis (延期)
                    this.setWithLogicalExpire(key, newObj, time, unit);

                } catch (Exception e) {
                    log.error("缓存重建异常", e);
                } finally {
                    // 🚨 铁律：无论如何必须释放信号量！
                    semaphore.release();
                    // 释放后清理 map 中的废弃锁，防止内存溢出
                    semaphoreMap.remove(key);
                }
            });
        }

        // 6. 👑 无论拿没拿到锁，主线程毫不留恋，拿着旧数据直接走人！
        return resultObj;
    }
}