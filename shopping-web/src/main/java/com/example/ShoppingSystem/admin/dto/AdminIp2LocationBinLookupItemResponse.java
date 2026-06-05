package com.example.ShoppingSystem.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminIp2LocationBinLookupItemResponse(String ip,
                                                    boolean matched,
                                                    String status,
                                                    String countryCode,
                                                    String countryName,
                                                    String region,
                                                    String city,
                                                    String district,
                                                    BigDecimal latitude,
                                                    BigDecimal longitude,
                                                    List<String> mismatchReasons) {
}
