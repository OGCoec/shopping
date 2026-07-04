package com.example.ShoppingSystem.mapper.risk;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * IP 信誉画像表查询 Mapper。
 * <p>
 * 当前仅用于“L6 高风险 IP 计数布隆初始化”场景：
 * 1) 统计 current_score &lt; 指定阈值的记录数量；
 * 2) 分页拉取满足条件的 IP 列表（分别来自 IPv4/IPv6 画像表）。
 */
@Mapper
public interface IpReputationProfileMapper {

    /**
     * 统计 IPv4 信誉画像表中低于阈值的 IP 数量。
     *
     * @param scoreThreshold 分数阈值（本期口径为 {@code current_score < 3000}）
     * @return 匹配数量
     */

    long countIpv4ByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold);

    /**
     * 统计 IPv6 信誉画像表中低于阈值的 IP 数量。
     *
     * @param scoreThreshold 分数阈值（本期口径为 {@code current_score < 3000}）
     * @return 匹配数量
     */

    long countIpv6ByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold);

    /**
     * 分页拉取 IPv4 信誉画像中的 L6 候选 IP。
     *
     * @param scoreThreshold 分数阈值（本期口径为 {@code current_score < 3000}）
     * @param limit 每页大小
     * @param offset 偏移量
     * @return IP 列表
     */

    List<String> listIpv4IpsByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold,
                                                   @Param("limit") int limit,
                                                   @Param("offset") long offset);

    /**
     * 分页拉取 IPv6 信誉画像中的 L6 候选 IP。
     *
     * @param scoreThreshold 分数阈值（本期口径为 {@code current_score < 3000}）
     * @param limit 每页大小
     * @param offset 偏移量
     * @return IP 列表
     */

    List<String> listIpv6IpsByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold,
                                                   @Param("limit") int limit,
                                                   @Param("offset") long offset);

    /**
     * 读取 IPv4 画像缓存行（用于 DB 层命中判断）。
     */

    Map<String, Object> findIpv4RiskCacheByIp(@Param("ip") String ip);

    /**
     * 读取 IPv6 画像缓存行（用于 DB 层命中判断）。
     */

    Map<String, Object> findIpv6RiskCacheByIp(@Param("ip") String ip);

    /**
     * 管理端分页读取 IPv4 信誉画像。
     */

    List<Map<String, Object>> listIpv4AdminRiskProfiles(@Param("country") String country,
                                                        @Param("minScore") Integer minScore,
                                                        @Param("maxScoreExclusive") Integer maxScoreExclusive,
                                                        @Param("ipQueryPattern") String ipQueryPattern);

    /**
     * 管理端分页读取 IPv6 信誉画像。
     */

    List<Map<String, Object>> listIpv6AdminRiskProfiles(@Param("country") String country,
                                                        @Param("minScore") Integer minScore,
                                                        @Param("maxScoreExclusive") Integer maxScoreExclusive,
                                                        @Param("ipQueryPattern") String ipQueryPattern);

    /**
     * IPv4 画像 Upsert。
     */

    int upsertIpv4RiskProfile(@Param("ip") String ip,
                              @Param("ipType") String ipType,
                              @Param("country") String country,
                              @Param("region") String region,
                              @Param("city") String city,
                              @Param("asn") String asn,
                              @Param("providerName") String providerName,
                              @Param("latitude") BigDecimal latitude,
                              @Param("longitude") BigDecimal longitude,
                              @Param("isDatacenter") boolean isDatacenter,
                              @Param("isVpn") boolean isVpn,
                              @Param("isProxy") boolean isProxy,
                              @Param("isTor") boolean isTor,
                              @Param("providerScore") int providerScore,
                              @Param("referenceScore") int referenceScore,
                              @Param("baseScore") int baseScore,
                              @Param("currentScore") int currentScore,
                              @Param("sourceProvider") String sourceProvider,
                              @Param("rawJson") String rawJson,
                              @Param("queriedAt") OffsetDateTime queriedAt,
                              @Param("expiresAt") OffsetDateTime expiresAt);

    /**
     * IPv6 画像 Upsert。
     */

    int upsertIpv6RiskProfile(@Param("ip") String ip,
                              @Param("ipType") String ipType,
                              @Param("country") String country,
                              @Param("region") String region,
                              @Param("city") String city,
                              @Param("asn") String asn,
                              @Param("providerName") String providerName,
                              @Param("latitude") BigDecimal latitude,
                              @Param("longitude") BigDecimal longitude,
                              @Param("isDatacenter") boolean isDatacenter,
                              @Param("isVpn") boolean isVpn,
                              @Param("isProxy") boolean isProxy,
                              @Param("isTor") boolean isTor,
                              @Param("providerScore") int providerScore,
                              @Param("referenceScore") int referenceScore,
                              @Param("baseScore") int baseScore,
                              @Param("currentScore") int currentScore,
                              @Param("sourceProvider") String sourceProvider,
                              @Param("rawJson") String rawJson,
                              @Param("queriedAt") OffsetDateTime queriedAt,
                              @Param("expiresAt") OffsetDateTime expiresAt);

    int upsertIpv4GeoOnly(@Param("ip") String ip,
                          @Param("country") String country,
                          @Param("region") String region,
                          @Param("city") String city,
                          @Param("latitude") BigDecimal latitude,
                          @Param("longitude") BigDecimal longitude,
                          @Param("sourceProvider") String sourceProvider,
                          @Param("rawJson") String rawJson,
                          @Param("queriedAt") OffsetDateTime queriedAt);

    int upsertIpv6GeoOnly(@Param("ip") String ip,
                          @Param("country") String country,
                          @Param("region") String region,
                          @Param("city") String city,
                          @Param("latitude") BigDecimal latitude,
                          @Param("longitude") BigDecimal longitude,
                          @Param("sourceProvider") String sourceProvider,
                          @Param("rawJson") String rawJson,
                          @Param("queriedAt") OffsetDateTime queriedAt);

    Integer applyIpv4AutomationPenalty(@Param("ip") String ip,
                                       @Param("penaltyScore") int penaltyScore,
                                       @Param("seenAt") OffsetDateTime seenAt);


    Integer applyIpv6AutomationPenalty(@Param("ip") String ip,
                                       @Param("penaltyScore") int penaltyScore,
                                       @Param("seenAt") OffsetDateTime seenAt);

    /**
     * 管理端批量更新 IPv4 IP 分数（一次 SQL）。
     */

    int batchUpdateIpv4Scores(@Param("ips") List<String> ips,
                              @Param("targetScore") int targetScore);

    /**
     * 管理端批量更新 IPv6 IP 分数（一次 SQL）。
     */

    int batchUpdateIpv6Scores(@Param("ips") List<String> ips,
                              @Param("targetScore") int targetScore);
}
