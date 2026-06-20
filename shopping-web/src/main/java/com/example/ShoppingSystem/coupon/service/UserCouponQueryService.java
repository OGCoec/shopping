package com.example.ShoppingSystem.coupon.service;

import com.example.ShoppingSystem.Utils.HybridIdCodec;
import com.example.ShoppingSystem.coupon.dto.UserCouponMineCardResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponMineDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponMinePageResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateCardResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplateDetailResponse;
import com.example.ShoppingSystem.coupon.dto.UserCouponTemplatePageResponse;
import com.example.ShoppingSystem.config.datasource.CouponReadReplicaQueryExecutor;
import com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper;
import com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper;
import com.example.ShoppingSystem.mapper.coupon.UserCouponMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface UserCouponQueryService {
    public UserCouponTemplatePageResponse receivablePage(Long userId,
                                                         Integer rawPage,
                                                         Integer rawPageSize,
                                                         String rawName);

    public UserCouponTemplateDetailResponse receivableDetail(Long userId, String rawCouponTemplateId);

    public UserCouponMinePageResponse minePage(Long userId,
                                               Integer rawPage,
                                               Integer rawPageSize,
                                               String rawStatus);

    public UserCouponMineDetailResponse mineDetail(Long userId, String rawUserCouponId);
}
