package com.example.ShoppingSystem.admin.dto;

import java.util.List;

public record AdminIp2LocationVerifyLinksResponse(int requestedCount,
                                                  int threadPoolSize,
                                                  List<AdminIp2LocationVerifyLinkItem> found,
                                                  List<AdminIp2LocationVerifyLinkItem> notFound,
                                                  List<AdminIp2LocationVerifyLinkItem> failed) {
}
