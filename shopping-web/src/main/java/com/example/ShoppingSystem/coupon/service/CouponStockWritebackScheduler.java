package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CouponStockWritebackScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponStockWritebackScheduler.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final CouponTemplateMapper couponTemplateMapper;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public CouponStockWritebackScheduler(StringRedisTemplate stringRedisTemplate,
                                         CouponTemplateMapper couponTemplateMapper,
                                         ObjectMapper objectMapper,
                                         @Value("${shopping.coupon.stock-writeback-batch-size:200}") int batchSize,
                                         @Value("${shopping.coupon.stock-writeback-max-batches-per-run:10}") int maxBatchesPerRun) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.couponTemplateMapper = couponTemplateMapper;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize <= 0 ? 200 : batchSize;
        this.maxBatchesPerRun = Math.max(1, maxBatchesPerRun);
    }

    @Scheduled(fixedDelayString = "${shopping.coupon.stock-writeback-delay-ms:5000}")
    public void writebackDirtyStock() {
        int batchCount = 0;
        int totalPopped = 0;
        int totalWritten = 0;
        boolean failed = false;
        while (batchCount < maxBatchesPerRun) {
            List<String> couponIds = stringRedisTemplate.opsForSet()
                    .pop(CouponRedisKeys.STOCK_DIRTY_KEY, batchSize);
            if (couponIds == null || couponIds.isEmpty()) {
                break;
            }
            batchCount++;
            totalPopped += couponIds.size();

            List<String> stockKeys = couponIds.stream()
                    .map(CouponRedisKeys::stockKey)
                    .toList();
            List<String> stockValues = stringRedisTemplate.opsForValue().multiGet(stockKeys);
            List<Map<String, Object>> rows = buildRows(couponIds, stockValues);
            if (rows.isEmpty()) {
                if (couponIds.size() < batchSize) {
                    break;
                }
                continue;
            }

            try {
                couponTemplateMapper.batchUpdateRemainingQuantity(objectMapper.writeValueAsString(rows));
                totalWritten += rows.size();
            } catch (Exception e) {
                failed = true;
                addBackDirty(couponIds);
                log.warn("[Coupon] stock writeback batch failed, batch={}, couponCount={}",
                        batchCount, couponIds.size(), e);
                break;
            }
            if (couponIds.size() < batchSize) {
                break;
            }
        }
        if (batchCount > 0 || failed) {
            log.info("[Coupon] stock writeback finished, batches={}, popped={}, written={}, batchSize={}, maxBatches={}, failed={}",
                    batchCount, totalPopped, totalWritten, batchSize, maxBatchesPerRun, failed);
        }
    }

    private List<Map<String, Object>> buildRows(List<String> couponIds, List<String> stockValues) {
        List<Map<String, Object>> rows = new ArrayList<>(couponIds.size());
        for (int index = 0; index < couponIds.size(); index += 1) {
            String couponId = couponIds.get(index);
            String stockValue = stockValues == null || stockValues.size() <= index ? null : stockValues.get(index);
            if (stockValue == null || stockValue.isBlank()) {
                continue;
            }
            try {
                int remainingQuantity = Math.max(0, Integer.parseInt(stockValue.trim()));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("coupon_id_hex", HybridIdCodec.toHex(HybridIdCodec.fromBase62(couponId)));
                row.put("remaining_quantity", remainingQuantity);
                rows.add(row);
            } catch (IllegalArgumentException e) {
                log.warn("[Coupon] invalid stock dirty item skipped, couponId={}, stockValue={}", couponId, stockValue);
            }
        }
        return rows;
    }

    private void addBackDirty(List<String> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForSet().add(
                CouponRedisKeys.STOCK_DIRTY_KEY,
                couponIds.toArray(new String[0])
        );
    }
}
