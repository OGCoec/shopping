package com.example.ShoppingSystem.admin.service.risk;

import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListResponse;
public interface AdminRiskCreditScoreService {
    public AdminIpRiskListResponse listIpRiskProfiles(String family,
                                                      String country,
                                                      String level,
                                                      int page,
                                                      int pageSize,
                                                      String sort,
                                                      String q);

    public AdminIpRiskBatchUpdateResponse batchUpdateIpRiskScores(String family,
                                                                   AdminIpRiskBatchUpdateRequest request);

    public AdminDeviceRiskListResponse listDeviceRiskProfiles(String level, int page, int pageSize, String sort, String q);

    public AdminDeviceRiskDetailResponse getDeviceDetail(String deviceId);
}
