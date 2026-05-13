package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListResponse;
import com.example.ShoppingSystem.admin.service.AdminRiskCreditScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/risk-credit")
public class AdminRiskCreditScoreController {

    private final AdminRiskCreditScoreService adminRiskCreditScoreService;

    public AdminRiskCreditScoreController(AdminRiskCreditScoreService adminRiskCreditScoreService) {
        this.adminRiskCreditScoreService = adminRiskCreditScoreService;
    }

    @GetMapping("/ip/{family}")
    public AdminApiResponse<AdminIpRiskListResponse> listIpRiskProfiles(
            @PathVariable String family,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "risk_first") String sort,
            @RequestParam(required = false) String q) {
        return AdminApiResponse.ok(adminRiskCreditScoreService.listIpRiskProfiles(
                family,
                country,
                level,
                page,
                pageSize,
                sort,
                q
        ));
    }

    @PostMapping("/ip/{family}/batch-update")
    public AdminApiResponse<AdminIpRiskBatchUpdateResponse> batchUpdateIpRiskScores(
            @PathVariable String family,
            @RequestBody AdminIpRiskBatchUpdateRequest request) {
        try {
            return AdminApiResponse.ok(
                    adminRiskCreditScoreService.batchUpdateIpRiskScores(family, request));
        } catch (IllegalArgumentException e) {
            return AdminApiResponse.fail("ADMIN_RISK_IP_BATCH_INVALID", e.getMessage());
        }
    }

    @GetMapping("/device")
    public AdminApiResponse<AdminDeviceRiskListResponse> listDeviceRiskProfiles(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "risk_first") String sort,
            @RequestParam(required = false) String q) {
        return AdminApiResponse.ok(adminRiskCreditScoreService.listDeviceRiskProfiles(
                level,
                page,
                pageSize,
                sort,
                q
        ));
    }

    @GetMapping("/device/{deviceId}")
    public AdminApiResponse<AdminDeviceRiskDetailResponse> getDeviceDetail(
            @PathVariable String deviceId) {
        return AdminApiResponse.ok(adminRiskCreditScoreService.getDeviceDetail(deviceId));
    }
}
