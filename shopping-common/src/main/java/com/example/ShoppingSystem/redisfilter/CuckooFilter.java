package com.example.ShoppingSystem.redisfilter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 布谷鸟过滤器工具类
 * 基于 RedisBloom 模块的 Cuckoo Filter 实现
 *
 * 前置条件：Redis 服务端必须安装并启用 RedisBloom 模块
 */
@Slf4j
@Component
public class CuckooFilter {

    /** 配置元数据 Redis key 后缀 */
    private static final String CONFIG_SUFFIX = ":expected_capacity";

    // ========================== 批量 Lua 脚本 ==========================

    /**
     * ADD_ALL 脚本：批量添加 N 个元素（一次 Redis 往返）
     * <p>
     * KEYS[1] = 过滤器 key
     * ARGV[1..N] = 要添加的元素
     * <p>
     * 循环调用 CF.ADD，返回成功添加的元素数量
     */
    private static final String LUA_ADD_ALL = "local count = 0 " +
            "for i = 1, #ARGV do " +
            "  local ok = redis.call('CF.ADD', KEYS[1], ARGV[i]) " +
            "  if ok == 1 then count = count + 1 end " +
            "end " +
            "return count";

    /**
     * EXISTS_ALL 脚本：批量检查 N 个元素是否全部可能存在（一次 Redis 往返）
     * <p>
     * KEYS[1] = 过滤器 key
     * ARGV[1..N] = 要检查的元素
     * <p>
     * 任一元素不存在立即返回 0，全部存在返回 1
     */
    private static final String LUA_EXISTS_ALL = "for i = 1, #ARGV do " +
            "  if redis.call('CF.EXISTS', KEYS[1], ARGV[i]) == 0 then " +
            "    return 0 " +
            "  end " +
            "end " +
            "return 1";

    /**
     * DELETE_ALL 脚本：批量删除 N 个元素（一次 Redis 往返）
     * <p>
     * KEYS[1] = 过滤器 key
     * ARGV[1..N] = 要删除的元素
     * <p>
     * 循环调用 CF.DEL，返回成功删除的元素数量
     */
    private static final String LUA_DELETE_ALL = "local count = 0 " +
            "for i = 1, #ARGV do " +
            "  local ok = redis.call('CF.DEL', KEYS[1], ARGV[i]) " +
            "  if ok == 1 then count = count + 1 end " +
            "end " +
            "return count";

    private final StringRedisTemplate stringRedisTemplate;

    public CuckooFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 初始化布谷鸟过滤器（含配置一致性检查）
     * <p>
     * 自动检测 Redis 中已有过滤器的配置是否与当前请求一致：
     * <ul>
     * <li>过滤器不存在 → 直接创建</li>
     * <li>过滤器存在且配置一致 → 跳过创建，返回 true</li>
     * <li>过滤器存在但配置不一致 → 删除旧过滤器后重新创建</li>
     * </ul>
     * 这样可以防止修改容量常量后，对已存在的过滤器执行 CF.RESERVE 触发 RedisException。
     *
     * @param key      过滤器的 Redis key
     * @param capacity 预估容量
     * @return 是否创建成功（配置未变时也返回 true）
     */
    public Boolean reserve(String key, long capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "[CuckooFilter] capacity 必须 >= 1，当前值: " + capacity);
        }
        if (capacity < 200) {
            throw new IllegalArgumentException(
                    "[CuckooFilter] capacity 太小（当前值: " + capacity + "），建议设置为 200 以上以保证过滤器精度");
        }

        String configKey = key + CONFIG_SUFFIX;

        // ===== 配置一致性检查 =====
        if (Boolean.TRUE.equals(isExists(key))) {
            String storedCapacity = stringRedisTemplate.opsForValue().get(configKey);
            String currentCapacity = String.valueOf(capacity);

            if (currentCapacity.equals(storedCapacity)) {
                log.info("[CuckooFilter] 过滤器 {} 已存在且配置一致（容量: {}），跳过重新创建",
                        key, capacity);
                return true;
            }

            // 配置不一致，需要删除旧过滤器后重建
            log.warn("[CuckooFilter] 检测到过滤器 {} 配置变更（原容量: {} → 新容量: {}），删除旧过滤器并重建",
                    key, storedCapacity, capacity);
            deleteFilter(key);
            stringRedisTemplate.delete(configKey);
        }

        // ===== 创建过滤器 =====
        // 桶大小设置为4，哈希冲突率为2%-3%
        DefaultRedisScript<String> script = new DefaultRedisScript<>(
                "return redis.call('CF.RESERVE', KEYS[1], ARGV[1], 'BUCKETSIZE', '4')", String.class);
        String result = stringRedisTemplate.execute(script, Collections.singletonList(key), String.valueOf(capacity));

        boolean success = "OK".equals(result);
        if (success) {
            // 持久化当前配置，供下次启动时做一致性检查
            stringRedisTemplate.opsForValue().set(configKey, String.valueOf(capacity));
            log.info("[CuckooFilter] 过滤器 {} 创建成功，容量: {}", key, capacity);
        }
        return success;
    }

    /**
     * 判断过滤器键是否存在
     * 
     * @param key 过滤器的 Redis key
     * @return 是否存在
     */
    public Boolean isExists(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 强行删除整个过滤器
     * 
     * @param key 过滤器的 Redis key
     * @return 是否删除成功
     */
    public Boolean deleteFilter(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 添加元素到布谷鸟过滤器
     *
     * @param key  过滤器的 Redis key
     * @param item 要添加的元素（如骑手 ID）
     * @return 是否添加成功（true: 成功, false: 可能已存在或过滤器已满）
     */
    public Boolean add(String key, String item) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>("return redis.call('CF.ADD', KEYS[1], ARGV[1])",
                Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), item);
        return result != null && result == 1L;
    }

    /**
     * 添加元素到布谷鸟过滤器（long 类型 ID）
     *
     * @param key 过滤器的 Redis key
     * @param id  要添加的 ID
     * @return 是否添加成功
     */
    public Boolean add(String key, long id) {
        return add(key, String.valueOf(id));
    }

    /**
     * 添加元素到布谷鸟过滤器（byte[] 类型 ID）
     *
     * @param key 过滤器的 Redis key
     * @param id  要添加的字节数组 ID
     * @return 是否添加成功
     */
    public Boolean add(String key, byte[] id) {
        return add(key, new String(id, StandardCharsets.UTF_8));
    }

    /**
     * 检查元素是否存在于布谷鸟过滤器中
     *
     * @param key  过滤器的 Redis key
     * @param item 要检查的元素
     * @return true: 可能存在, false: 一定不存在
     */
    public Boolean exists(String key, String item) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>("return redis.call('CF.EXISTS', KEYS[1], ARGV[1])",
                Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), item);
        return result != null && result == 1L;
    }

    /**
     * 检查元素是否存在于布谷鸟过滤器中（long 类型 ID）
     *
     * @param key 过滤器的 Redis key
     * @param id  要检查的 ID
     * @return true: 可能存在, false: 一定不存在
     */
    public Boolean exists(String key, long id) {
        return exists(key, String.valueOf(id));
    }

    /**
     * 检查元素是否存在于布谷鸟过滤器中（byte[] 类型 ID）
     *
     * @param key 过滤器的 Redis key
     * @param id  要检查的字节数组 ID
     * @return true: 可能存在, false: 一定不存在
     */
    public Boolean exists(String key, byte[] id) {
        return exists(key, new String(id, StandardCharsets.UTF_8));
    }

    /**
     * 从布谷鸟过滤器中删除元素
     *
     * @param key  过滤器的 Redis key
     * @param item 要删除的元素
     * @return true: 删除成功, false: 元素不存在
     */
    public Boolean delete(String key, String item) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>("return redis.call('CF.DEL', KEYS[1], ARGV[1])",
                Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), item);
        return result != null && result == 1L;
    }

    /**
     * 从布谷鸟过滤器中删除元素（long 类型 ID）
     *
     * @param key 过滤器的 Redis key
     * @param id  要删除的 ID
     * @return true: 删除成功, false: 元素不存在
     */
    public Boolean delete(String key, long id) {
        return delete(key, String.valueOf(id));
    }

    /**
     * 从布谷鸟过滤器中删除元素（byte[] 类型 ID）
     *
     * @param key 过滤器的 Redis key
     * @param id  要删除的字节数组 ID
     * @return true: 删除成功, false: 元素不存在
     */
    public Boolean delete(String key, byte[] id) {
        return delete(key, new String(id, StandardCharsets.UTF_8));
    }

    // ========================== 批量操作 ==========================

    /**
     * 批量添加多个元素到布谷鸟过滤器（一次 Redis 往返）
     * <p>
     * 将所有元素拼接到一次 Lua 调用中循环执行 CF.ADD，
     * 避免 N 次独立调用带来的 N 次网络 RTT。
     *
     * @param key   过滤器的 Redis key
     * @param items 要添加的元素列表（String）
     * @return 成功添加的元素数量
     */
    public long addAllItems(String key, List<String> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_ADD_ALL, Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key),
                items.toArray(new String[0]));
        return result != null ? result : 0;
    }

    /**
     * 批量添加多个元素（Long 类型 ID 列表）
     */
    public long addAllLongs(String key, List<Long> ids) {
        List<String> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            items.add(String.valueOf(id));
        }
        return addAllItems(key, items);
    }

    /**
     * 批量添加多个元素（byte[] 类型 ID 列表）
     */
    public long addAll(String key, List<byte[]> ids) {
        List<String> items = new ArrayList<>(ids.size());
        for (byte[] id : ids) {
            items.add(new String(id, StandardCharsets.UTF_8));
        }
        return addAllItems(key, items);
    }

    /**
     * 批量检查多个元素是否全部可能存在于过滤器中（一次 Redis 往返）
     * <p>
     * 将所有元素拼接到一次 Lua 调用中循环执行 CF.EXISTS，
     * 任一元素不存在立即返回 false。
     *
     * @param key   过滤器的 Redis key
     * @param items 要检查的元素列表（String）
     * @return true: 所有元素可能存在, false: 至少有一个一定不存在
     */
    public Boolean existsAllItems(String key, List<String> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_EXISTS_ALL, Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key),
                items.toArray(new String[0]));
        return result != null && result == 1L;
    }

    /**
     * 批量检查多个元素是否全部可能存在（Long 类型 ID 列表）
     */
    public Boolean existsAllLongs(String key, List<Long> ids) {
        List<String> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            items.add(String.valueOf(id));
        }
        return existsAllItems(key, items);
    }

    /**
     * 批量检查多个元素是否全部可能存在（byte[] 类型 ID 列表）
     */
    public Boolean existsAll(String key, List<byte[]> ids) {
        List<String> items = new ArrayList<>(ids.size());
        for (byte[] id : ids) {
            items.add(new String(id, StandardCharsets.UTF_8));
        }
        return existsAllItems(key, items);
    }

    /**
     * 批量删除多个元素（一次 Redis 往返）
     * <p>
     * 将所有元素拼接到一次 Lua 调用中循环执行 CF.DEL，
     * 避免 N 次独立调用带来的 N 次网络 RTT。
     *
     * @param key   过滤器的 Redis key
     * @param items 要删除的元素列表（String）
     * @return 成功删除的元素数量
     */
    public long deleteAllItems(String key, List<String> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_DELETE_ALL, Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key),
                items.toArray(new String[0]));
        return result != null ? result : 0;
    }

    /**
     * 批量删除多个元素（Long 类型 ID 列表）
     */
    public long deleteAllLongs(String key, List<Long> ids) {
        List<String> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            items.add(String.valueOf(id));
        }
        return deleteAllItems(key, items);
    }

    /**
     * 批量删除多个元素（byte[] 类型 ID 列表）
     */
    public long deleteAll(String key, List<byte[]> ids) {
        List<String> items = new ArrayList<>(ids.size());
        for (byte[] id : ids) {
            items.add(new String(id, StandardCharsets.UTF_8));
        }
        return deleteAllItems(key, items);
    }
}
