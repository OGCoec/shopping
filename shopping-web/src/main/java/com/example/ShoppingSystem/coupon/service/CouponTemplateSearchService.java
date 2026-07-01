package com.example.ShoppingSystem.coupon.service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface CouponTemplateSearchService {
    public record SearchResult(long total, List<String> ids, Map<String, String> highlightedNames) {
        }

    public SearchResult searchAdminTemplateIds(String name,
                                               String status,
                                               OffsetDateTime receiveStartAtFrom,
                                               OffsetDateTime receiveEndAtTo,
                                               int page,
                                               int pageSize);

    public SearchResult searchActiveReceivableTemplateIds(String name,
                                                          OffsetDateTime now,
                                                          int page,
                                                          int pageSize);
}
