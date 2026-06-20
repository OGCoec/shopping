package com.example.ShoppingSystem.coupon.service.impl.CouponRedisCacheService;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.ShoppingSystem.coupon.service.CouponRedisCacheService;
import com.example.ShoppingSystem.coupon.service.CouponRedisKeys;
@Service
public class CouponRedisCacheServiceImpl implements CouponRedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(CouponRedisCacheService.class);

    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponScopeMapper couponScopeMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public CouponRedisCacheServiceImpl(CouponTemplateMapper couponTemplateMapper,
                                   CouponScopeMapper couponScopeMapper,
                                   UserCouponMapper userCouponMapper,
                                   StringRedisTemplate stringRedisTemplate) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.couponScopeMapper = couponScopeMapper;
        this.userCouponMapper = userCouponMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean writeCouponToRedis(byte[] couponTemplateId) {
        Map<String, Object> template = couponTemplateMapper.findById(couponTemplateId);
        if (template == null || template.isEmpty()) {
            return false;
        }
        String couponId = HybridIdCodec.toBase62FromDatabaseValue(template.get("id"));
        String status = text(template.get("status"));
        if ("DELETED".equals(status)) {
            deleteCouponRuntime(couponId);
            return true;
        }
        List<Map<String, Object>> scopes = couponScopeMapper.listByTemplateId(couponTemplateId);
        List<Map<String, Object>> claimed = userCouponMapper.listClaimedByTemplateId(couponTemplateId);
        writeCouponToRedis(template, scopes, claimed);
        return true;
    }

    public void markDisabled(String couponId) {
        if (couponId == null || couponId.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForHash().put(CouponRedisKeys.templateKey(couponId), "status", "DISABLED");
    }

    public void deleteCouponRuntime(String couponId) {
        if (couponId == null || couponId.isBlank()) {
            return;
        }
        List<String> keys = List.of(
                CouponRedisKeys.templateKey(couponId),
                CouponRedisKeys.stockKey(couponId),
                CouponRedisKeys.scopeKey(couponId),
                CouponRedisKeys.claimedKey(couponId)
        );
        stringRedisTemplate.delete(keys);
    }

    private void writeCouponToRedis(Map<String, Object> template,
                                    List<Map<String, Object>> scopes,
                                    List<Map<String, Object>> claimed) {
        String couponId = HybridIdCodec.toBase62FromDatabaseValue(template.get("id"));
        String templateKey = CouponRedisKeys.templateKey(couponId);
        String stockKey = CouponRedisKeys.stockKey(couponId);
        String scopeKey = CouponRedisKeys.scopeKey(couponId);
        String claimedKey = CouponRedisKeys.claimedKey(couponId);
        Map<String, String> templateHash = new LinkedHashMap<>();
        templateHash.put("couponId", couponId);
        templateHash.put("status", text(template.get("status")));
        templateHash.put("discountType", text(template.get("discountType")));
        templateHash.put("thresholdAmountYuan", text(template.get("thresholdAmountYuan")));
        templateHash.put("discountAmountYuan", text(template.get("discountAmountYuan")));
        templateHash.put("discountRate", text(template.get("discountRate")));
        templateHash.put("maxDiscountAmountYuan", text(template.get("maxDiscountAmountYuan")));
        templateHash.put("perUserLimit", text(template.get("perUserLimit")));
        templateHash.put("scopeType", text(template.get("scopeType")));
        templateHash.put("receiveStartAtEpochMs", epochMillis(template.get("receiveStartAt")));
        templateHash.put("receiveEndAtEpochMs", epochMillis(template.get("receiveEndAt")));
        templateHash.put("validStartAtEpochMs", epochMillis(template.get("validStartAt")));
        templateHash.put("validEndAtEpochMs", epochMillis(template.get("validEndAt")));
        templateHash.put("version", text(template.get("version")));

        List<String> scopeValues = scopeValues(scopes);
        Map<String, String> claimedValues = claimedValues(claimed);
        String remainingQuantity = text(template.get("remainingQuantity"));
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"rawtypes", "unchecked"})
                public Object execute(RedisOperations operations) {
                    operations.delete(List.of(templateKey, stockKey, scopeKey, claimedKey));
                    operations.opsForHash().putAll(templateKey, templateHash);
                    operations.persist(templateKey);
                    operations.opsForValue().set(stockKey, remainingQuantity);
                    operations.persist(stockKey);
                    if (!scopeValues.isEmpty()) {
                        operations.opsForSet().add(scopeKey, scopeValues.toArray(new String[0]));
                        operations.persist(scopeKey);
                    }
                    if (!claimedValues.isEmpty()) {
                        operations.opsForHash().putAll(claimedKey, claimedValues);
                        operations.persist(claimedKey);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("[Coupon] write coupon redis cache failed, couponId={}", couponId, e);
        }
    }

    private List<String> scopeValues(List<Map<String, Object>> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(scopes.size());
        for (Map<String, Object> scope : scopes) {
            String type = text(scope.get("scopeTargetType"));
            if ("CATEGORY".equals(type)) {
                values.add(text(scope.get("categoryId")));
            } else if ("SPU".equals(type)) {
                values.add(text(scope.get("spuId")));
            } else if ("SKU".equals(type)) {
                values.add(HybridIdCodec.toBase62FromDatabaseValue(scope.get("skuId")));
            }
        }
        return values;
    }

    private Map<String, String> claimedValues(List<Map<String, Object>> claimed) {
        if (claimed == null || claimed.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : claimed) {
            String userId = text(row.get("userId"));
            String userCouponId = HybridIdCodec.toBase62FromDatabaseValue(row.get("userCouponId"));
            if (!userId.isBlank() && !userCouponId.isBlank()) {
                values.put(userId, userCouponId);
            }
        }
        return values;
    }

    private String epochMillis(Object value) {
        if (value instanceof OffsetDateTime time) {
            return String.valueOf(time.toInstant().toEpochMilli());
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return String.valueOf(timestamp.toInstant().toEpochMilli());
        }
        if (value instanceof java.util.Date date) {
            return String.valueOf(date.toInstant().toEpochMilli());
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
