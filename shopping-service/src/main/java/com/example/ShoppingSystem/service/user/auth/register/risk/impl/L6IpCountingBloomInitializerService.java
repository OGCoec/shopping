package com.example.ShoppingSystem.service.user.auth.register.risk.impl;

import com.example.ShoppingSystem.mapper.IpReputationProfileMapper;
import com.example.ShoppingSystem.redisfilter.CountingBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L6 高风险 IP 计数布隆初始化服务。
 * <p>
 * 设计目标：
 * 1) 启动时将“current_score < 3000（即 L6）”的 IPv4/IPv6 IP 预热到计数布隆；
 * 2) 仅初始化 L6，明确不纳入 L5；
 * 3) 过滤器容量按真实数据量动态计算，避免拍脑袋配置；
 * 4) 每个 bucket 使用 1 byte（由 {@link CountingBloomFilter} 实现保证）。
 * <p>
 * 本类仅负责“初始化/重建”，不负责后续查询链路接入（caffeine/redis/db/api）决策。
 */
@Service
public class L6IpCountingBloomInitializerService {

    private static final Logger log = LoggerFactory.getLogger(L6IpCountingBloomInitializerService.class);

    /**
     * 计数布隆最低容量保护值。
     * <p>
     * {@link CountingBloomFilter#init(String, int, int)} 内部要求 capacity >= 200，
     * 这里保持一致，避免阈值不一致导致初始化失败。
     */
    private static final int MIN_CAPACITY = 200;

    /**
     * 计数布隆哈希次数下界。
     */
    private static final int MIN_HASH_COUNT = 4;

    /**
     * 计数布隆哈希次数上界。
     */
    private static final int MAX_HASH_COUNT = 25;

    private final CountingBloomFilter countingBloomFilter;
    private final IpReputationProfileMapper ipReputationProfileMapper;

    /**
     * 是否启用启动初始化。
     */
    @Value("${register.ip-l6-counting-bloom.enabled:true}")
    private boolean enabled;

    /**
     * L6 分数阈值，按业务口径要求：仅 current_score < 3000 纳入。
     */
    @Value("${register.ip-l6-counting-bloom.score-threshold:3000}")
    private int scoreThreshold;

    /**
     * 固定布隆容量（bucket 总数）。
     * <p>
     * 当前按你的要求先写死，不再根据 n/p 动态计算。
     */
    @Value("${register.ip-l6-counting-bloom.capacity:2000000}")
    private int fixedCapacity;

    /**
     * 固定哈希次数。
     * <p>
     * 当前按你的要求先写死，不再动态计算。
     */
    @Value("${register.ip-l6-counting-bloom.hash-count:7}")
    private int fixedHashCount;

    /**
     * DB 分页加载大小。
     */
    @Value("${register.ip-l6-counting-bloom.page-size:2000}")
    private int pageSize;

    /**
     * Redis 中的 IPv4 布隆 key。
     */
    @Value("${register.ip-l6-counting-bloom.ipv4-key:register:ip:l6:cbf:ipv4}")
    private String ipv4FilterKey;

    /**
     * Redis 中的 IPv6 布隆 key。
     */
    @Value("${register.ip-l6-counting-bloom.ipv6-key:register:ip:l6:cbf:ipv6}")
    private String ipv6FilterKey;

    public L6IpCountingBloomInitializerService(CountingBloomFilter countingBloomFilter,
                                               IpReputationProfileMapper ipReputationProfileMapper) {
        this.countingBloomFilter = countingBloomFilter;
        this.ipReputationProfileMapper = ipReputationProfileMapper;
    }

    /**
     * 启动时重建 L6 计数布隆（IPv4 + IPv6）。
     * <p>
     * 说明：
     * 1) 采用 reinit 先清空旧过滤器再重建，避免“重复启动导致 counter 叠加”；
     * 2) 仅纳入 current_score < scoreThreshold 的 IP；
     * 3) 若某个族（IPv4/IPv6）数据为空，仍会创建空过滤器，保证后续 exists() 可正常调用。
     */
    public void rebuildL6FiltersOnStartup() {
        if (!enabled) {
            log.info("L6计数布隆初始化已关闭：register.ip-l6-counting-bloom.enabled=false");
            return;
        }

        long start = System.currentTimeMillis();
        int safeThreshold = Math.max(scoreThreshold, 1);
        int safePageSize = Math.max(pageSize, 100);
        int safeCapacity = Math.max(MIN_CAPACITY, fixedCapacity);
        int safeHashCount = Math.max(MIN_HASH_COUNT, Math.min(MAX_HASH_COUNT, fixedHashCount));

        initializeSingleFamilyFilter(
                "IPv4",
                ipv4FilterKey,
                safeThreshold,
                safePageSize,
                safeCapacity,
                safeHashCount,
                ipReputationProfileMapper.countIpv4ByCurrentScoreLessThan(safeThreshold),
                (limit, offset) -> ipReputationProfileMapper.listIpv4IpsByCurrentScoreLessThan(safeThreshold, limit, offset));

        initializeSingleFamilyFilter(
                "IPv6",
                ipv6FilterKey,
                safeThreshold,
                safePageSize,
                safeCapacity,
                safeHashCount,
                ipReputationProfileMapper.countIpv6ByCurrentScoreLessThan(safeThreshold),
                (limit, offset) -> ipReputationProfileMapper.listIpv6IpsByCurrentScoreLessThan(safeThreshold, limit, offset));

        log.info("L6计数布隆初始化完成：scoreThreshold<{}，总耗时={}ms", safeThreshold, System.currentTimeMillis() - start);
    }

    /**
     * 初始化单个 IP 家族（IPv4 或 IPv6）的计数布隆。
     *
     * @param familyLabel 展示标签（IPv4/IPv6）
     * @param filterKey Redis 中过滤器 key
     * @param threshold 分数阈值
     * @param batchSize 分页大小
     * @param fixedCapacity 固定容量（bucket 数）
     * @param fixedHashCount 固定哈希次数
     * @param totalRows 满足条件的总记录数
     * @param pageLoader 分页加载器
     */
    private void initializeSingleFamilyFilter(String familyLabel,
                                              String filterKey,
                                              int threshold,
                                              int batchSize,
                                              int fixedCapacity,
                                              int fixedHashCount,
                                              long totalRows,
                                              PageLoader pageLoader) {
        countingBloomFilter.reinit(filterKey, fixedCapacity, fixedHashCount);

        long offset = 0L;
        long loadedRows = 0L;
        while (true) {
            List<String> page = pageLoader.load(batchSize, offset);
            if (page == null || page.isEmpty()) {
                break;
            }
            long added = countingBloomFilter.addAllItems(filterKey, page);
            loadedRows += added;
            offset += page.size();
        }

        log.info(
                "L6计数布隆初始化明细：family={}，filterKey={}，条件=current_score<{}，dbRows={}，loadedRows={}，capacity={}，hashCount={}，targetFpp={}",
                familyLabel,
                filterKey,
                threshold,
                totalRows,
                loadedRows,
                fixedCapacity,
                fixedHashCount);
    }

    @FunctionalInterface
    private interface PageLoader {
        List<String> load(int limit, long offset);
    }
}
