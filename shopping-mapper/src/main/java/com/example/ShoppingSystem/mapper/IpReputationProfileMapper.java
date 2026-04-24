package com.example.ShoppingSystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
    @Select("""
            SELECT COUNT(1)
            FROM ipv4_reputation_profile
            WHERE current_score < #{scoreThreshold}
            """)
    long countIpv4ByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold);

    /**
     * 统计 IPv6 信誉画像表中低于阈值的 IP 数量。
     *
     * @param scoreThreshold 分数阈值（本期口径为 {@code current_score < 3000}）
     * @return 匹配数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM ipv6_reputation_profile
            WHERE current_score < #{scoreThreshold}
            """)
    long countIpv6ByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold);

    /**
     * 分页拉取 IPv4 信誉画像中的 L6 候选 IP。
     *
     * @param scoreThreshold 分数阈值（本期口径为 {@code current_score < 3000}）
     * @param limit 每页大小
     * @param offset 偏移量
     * @return IP 列表
     */
    @Select("""
            SELECT ip
            FROM ipv4_reputation_profile
            WHERE current_score < #{scoreThreshold}
            ORDER BY ip
            LIMIT #{limit}
            OFFSET #{offset}
            """)
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
    @Select("""
            SELECT ip
            FROM ipv6_reputation_profile
            WHERE current_score < #{scoreThreshold}
            ORDER BY ip
            LIMIT #{limit}
            OFFSET #{offset}
            """)
    List<String> listIpv6IpsByCurrentScoreLessThan(@Param("scoreThreshold") int scoreThreshold,
                                                   @Param("limit") int limit,
                                                   @Param("offset") long offset);

    /**
     * 读取 IPv4 画像缓存行（用于 DB 层命中判断）。
     */
    @Select("""
            SELECT ip,
                   current_score,
                   country,
                   EXTRACT(EPOCH FROM expires_at) * 1000 AS expires_at_epoch_millis,
                   raw_json::text AS raw_json_text
            FROM ipv4_reputation_profile
            WHERE ip = #{ip}
            LIMIT 1
            """)
    Map<String, Object> findIpv4RiskCacheByIp(@Param("ip") String ip);

    /**
     * 读取 IPv6 画像缓存行（用于 DB 层命中判断）。
     */
    @Select("""
            SELECT ip,
                   current_score,
                   country,
                   EXTRACT(EPOCH FROM expires_at) * 1000 AS expires_at_epoch_millis,
                   raw_json::text AS raw_json_text
            FROM ipv6_reputation_profile
            WHERE ip = #{ip}
            LIMIT 1
            """)
    Map<String, Object> findIpv6RiskCacheByIp(@Param("ip") String ip);

    /**
     * IPv4 画像 Upsert。
     */
    @Update("""
            INSERT INTO ipv4_reputation_profile (
                ip, ip_type, country, asn, provider_name, latitude, longitude, is_datacenter, is_vpn, is_proxy, is_tor,
                provider_score, reference_score, base_score, current_score,
                source_provider, raw_json, queried_at, expires_at, last_seen_at
            ) VALUES (
                #{ip}, #{ipType}, #{country}, #{asn}, #{providerName}, #{latitude}, #{longitude}, #{isDatacenter}, #{isVpn}, #{isProxy}, #{isTor},
                #{providerScore}, #{referenceScore}, #{baseScore}, #{currentScore},
                #{sourceProvider}, CAST(#{rawJson} AS jsonb), #{queriedAt}, #{expiresAt}, #{queriedAt}
            )
            ON CONFLICT (ip) DO UPDATE
            SET ip_type = EXCLUDED.ip_type,
                country = EXCLUDED.country,
                asn = EXCLUDED.asn,
                provider_name = EXCLUDED.provider_name,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                is_datacenter = EXCLUDED.is_datacenter,
                is_vpn = EXCLUDED.is_vpn,
                is_proxy = EXCLUDED.is_proxy,
                is_tor = EXCLUDED.is_tor,
                provider_score = EXCLUDED.provider_score,
                reference_score = EXCLUDED.reference_score,
                base_score = EXCLUDED.base_score,
                current_score = EXCLUDED.current_score,
                source_provider = EXCLUDED.source_provider,
                raw_json = EXCLUDED.raw_json,
                queried_at = EXCLUDED.queried_at,
                expires_at = EXCLUDED.expires_at,
                last_seen_at = EXCLUDED.last_seen_at
            """)
    int upsertIpv4RiskProfile(@Param("ip") String ip,
                              @Param("ipType") String ipType,
                              @Param("country") String country,
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
    @Update("""
            INSERT INTO ipv6_reputation_profile (
                ip, ip_type, country, asn, provider_name, latitude, longitude, is_datacenter, is_vpn, is_proxy, is_tor,
                provider_score, reference_score, base_score, current_score,
                source_provider, raw_json, queried_at, expires_at, last_seen_at
            ) VALUES (
                #{ip}, #{ipType}, #{country}, #{asn}, #{providerName}, #{latitude}, #{longitude}, #{isDatacenter}, #{isVpn}, #{isProxy}, #{isTor},
                #{providerScore}, #{referenceScore}, #{baseScore}, #{currentScore},
                #{sourceProvider}, CAST(#{rawJson} AS jsonb), #{queriedAt}, #{expiresAt}, #{queriedAt}
            )
            ON CONFLICT (ip) DO UPDATE
            SET ip_type = EXCLUDED.ip_type,
                country = EXCLUDED.country,
                asn = EXCLUDED.asn,
                provider_name = EXCLUDED.provider_name,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                is_datacenter = EXCLUDED.is_datacenter,
                is_vpn = EXCLUDED.is_vpn,
                is_proxy = EXCLUDED.is_proxy,
                is_tor = EXCLUDED.is_tor,
                provider_score = EXCLUDED.provider_score,
                reference_score = EXCLUDED.reference_score,
                base_score = EXCLUDED.base_score,
                current_score = EXCLUDED.current_score,
                source_provider = EXCLUDED.source_provider,
                raw_json = EXCLUDED.raw_json,
                queried_at = EXCLUDED.queried_at,
                expires_at = EXCLUDED.expires_at,
                last_seen_at = EXCLUDED.last_seen_at
            """)
    int upsertIpv6RiskProfile(@Param("ip") String ip,
                              @Param("ipType") String ipType,
                              @Param("country") String country,
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
}
