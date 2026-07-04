package com.example.ShoppingSystem.admin.controller.risk;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminDeviceRiskListResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateRequest;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskBatchUpdateResponse;
import com.example.ShoppingSystem.admin.dto.AdminIpRiskListResponse;
import com.example.ShoppingSystem.admin.service.risk.AdminRiskCreditScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台风控信用分", description = "后台IP和设备风控信用分接口")
@RestController
@RequestMapping("/shopping/admin/api/risk-credit")
public class AdminRiskCreditScoreController {

    private final AdminRiskCreditScoreService adminRiskCreditScoreService;

    public AdminRiskCreditScoreController(AdminRiskCreditScoreService adminRiskCreditScoreService) {
        this.adminRiskCreditScoreService = adminRiskCreditScoreService;
    }

    @Operation(summary = "分页查询IP风控信用分")
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

    @Operation(summary = "批量更新IP风控信用分")
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

    @Operation(summary = "分页查询设备风控信用分")
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

    @Operation(summary = "查询设备风控详情")
    @GetMapping("/device/{deviceId}")
    public AdminApiResponse<AdminDeviceRiskDetailResponse> getDeviceDetail(
            @PathVariable String deviceId) {
        return AdminApiResponse.ok(adminRiskCreditScoreService.getDeviceDetail(deviceId));
    }
}
