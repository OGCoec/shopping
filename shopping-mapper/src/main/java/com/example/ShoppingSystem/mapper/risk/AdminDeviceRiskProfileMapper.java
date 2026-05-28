package com.example.ShoppingSystem.mapper.risk;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDeviceRiskProfileMapper {

    List<Map<String, Object>> listDeviceRiskProfiles(@Param("riskLevel") String riskLevel,
                                                     @Param("minScore") Integer minScore,
                                                     @Param("maxScoreExclusive") Integer maxScoreExclusive,
                                                     @Param("queryPattern") String queryPattern,
                                                     @Param("sort") String sort);

    Map<String, Object> findDeviceById(@Param("deviceIdHex") String deviceIdHex);

    List<Map<String, Object>> listScoreEventsByDeviceId(@Param("deviceIdHex") String deviceIdHex,
                                                        @Param("limit") int limit);
}
