package com.example.ShoppingSystem.mapper.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    int insertOrder(@Param("orderNo") String orderNo,
                    @Param("userId") Long userId,
                    @Param("status") String status,
                     @Param("totalAmountYuan") BigDecimal totalAmountYuan,
                     @Param("discountAmountYuan") BigDecimal discountAmountYuan,
                     @Param("payAmountYuan") BigDecimal payAmountYuan,
                     @Param("requiredPoints") long requiredPoints,
                     @Param("userCouponId") byte[] userCouponId,
                    @Param("idempotencyKey") String idempotencyKey,
                    @Param("expireAt") OffsetDateTime expireAt,
                    @Param("createdAt") OffsetDateTime createdAt);

    int insertOrderItems(@Param("itemsJson") String itemsJson);

    int batchUpsertOrders(@Param("ordersJson") String ordersJson);

    int batchInsertOrderItems(@Param("itemsJson") String itemsJson);

    Map<String, Object> lockOrderState(@Param("orderNo") String orderNo);

    Boolean tryLockOrderState(@Param("orderNo") String orderNo);

    Map<String, Object> findOrderByOrderNoForUser(@Param("orderNo") String orderNo,
                                                   @Param("userId") Long userId);

    Map<String, Object> findOrderByOrderNoForUserForUpdate(@Param("orderNo") String orderNo,
                                                           @Param("userId") Long userId);

    Map<String, Object> findOrderByOrderNo(@Param("orderNo") String orderNo);

    Map<String, Object> findOrderByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey,
                                                  @Param("userId") Long userId);

    List<Map<String, Object>> listOrderItems(@Param("orderNo") String orderNo);

    Map<String, Object> summarizeOrderItemPoints(@Param("orderNo") String orderNo);

    Map<String, Object> deductUserPoints(@Param("userId") Long userId,
                                         @Param("usedPoints") long usedPoints);

    List<Map<String, Object>> pageOrdersByUser(@Param("userId") Long userId,
                                               @Param("status") String status,
                                               @Param("limit") int limit,
                                               @Param("offset") long offset);

    long countOrdersByUser(@Param("userId") Long userId,
                           @Param("status") String status);

    List<Map<String, Object>> pageOrdersForAdmin(@Param("status") String status,
                                                 @Param("orderNo") String orderNo,
                                                 @Param("limit") int limit,
                                                 @Param("offset") long offset);

    long countOrdersForAdmin(@Param("status") String status,
                             @Param("orderNo") String orderNo);

    Map<String, Object> cancelPendingOrder(@Param("orderNo") String orderNo,
                                           @Param("userId") Long userId,
                                           @Param("now") OffsetDateTime now);

    Map<String, Object> startClosingExpiredOrder(@Param("orderNo") String orderNo,
                                                 @Param("now") OffsetDateTime now,
                                                 @Param("closingDeadline") OffsetDateTime closingDeadline);

    Map<String, Object> closeClosingOrder(@Param("orderNo") String orderNo,
                                          @Param("now") OffsetDateTime now);

    Map<String, Object> markPaidOrder(@Param("orderNo") String orderNo,
                                      @Param("paidAt") OffsetDateTime paidAt);

    List<Map<String, Object>> batchMarkPaidAndClassify(@Param("callbacksJson") String callbacksJson);

    Map<String, Object> markPendingPaidOrderForUser(@Param("orderNo") String orderNo,
                                                    @Param("userId") Long userId,
                                                    @Param("paidAt") OffsetDateTime paidAt);

    Map<String, Object> markPointsPaidOrderForUser(@Param("orderNo") String orderNo,
                                                    @Param("userId") Long userId,
                                                    @Param("paidAt") OffsetDateTime paidAt,
                                                    @Param("requestReceivedAt") OffsetDateTime requestReceivedAt,
                                                    @Param("usedPoints") long usedPoints);
}
