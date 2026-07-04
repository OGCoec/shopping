package com.example.ShoppingSystem.admin.controller.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRestoreRequest;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRestoreResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskScoreEventListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountScoreAdjustRequest;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountScoreAdjustResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountSelfTerminationListResponse;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminSessionMeResponse;
import com.example.ShoppingSystem.admin.service.account.AdminAccountManagementService;
import com.example.ShoppingSystem.admin.service.auth.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台账号管理", description = "后台账号信用分和账号终止管理接口")
@RestController
@RequestMapping("/shopping/admin/api/accounts")
public class AdminAccountManagementController {

    private final AdminAccountManagementService adminAccountManagementService;
    private final AdminSessionService adminSessionService;

    public AdminAccountManagementController(AdminAccountManagementService adminAccountManagementService,
                                            AdminSessionService adminSessionService) {
        this.adminAccountManagementService = adminAccountManagementService;
        this.adminSessionService = adminSessionService;
    }

    @Operation(summary = "分页查询账号信用分")
    @GetMapping("/credit")
    public AdminApiResponse<AccountCreditListResponse> listAccountCredits(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return AdminApiResponse.ok(adminAccountManagementService.listAccountCredits(
                userId,
                email,
                phone,
                status,
                riskLevel,
                page,
                pageSize
        ));
    }

    @Operation(summary = "查询账号信用分详情")
    @GetMapping("/credit/{userId}")
    public AdminApiResponse<AccountCreditDetailResponse> getAccountCreditDetail(@PathVariable Long userId) {
        return AdminApiResponse.ok(adminAccountManagementService.getAccountCreditDetail(userId));
    }

    @Operation(summary = "查询账号信用分事件")
    @GetMapping("/credit/{userId}/events")
    public AdminApiResponse<AccountRiskScoreEventListResponse> listAccountCreditEvents(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return AdminApiResponse.ok(adminAccountManagementService.listAccountCreditEvents(userId, page, pageSize));
    }

    @Operation(summary = "调整账号信用分")
    @PostMapping("/credit/{userId}/adjust")
    public AdminApiResponse<AccountScoreAdjustResponse> adjustAccountScore(
            @PathVariable Long userId,
            @RequestBody AccountScoreAdjustRequest request,
            HttpServletRequest servletRequest) {
        return AdminApiResponse.ok(adminAccountManagementService.adjustAccountScore(
                userId,
                request,
                currentAdmin(servletRequest)
        ));
    }

    @Operation(summary = "分页查询用户自助注销记录")
    @GetMapping("/terminations/self")
    public AdminApiResponse<AccountSelfTerminationListResponse> listSelfTerminations(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return AdminApiResponse.ok(adminAccountManagementService.listSelfTerminations(
                scope,
                userId,
                email,
                phone,
                page,
                pageSize
        ));
    }

    @Operation(summary = "恢复用户自助注销账号")
    @PostMapping("/terminations/self/{id}/restore")
    public AdminApiResponse<AccountRestoreResponse> restoreSelfTermination(
            @PathVariable Long id,
            @RequestBody(required = false) AccountRestoreRequest request,
            HttpServletRequest servletRequest) {
        return AdminApiResponse.ok(adminAccountManagementService.restoreSelfTermination(
                id,
                request,
                currentAdmin(servletRequest)
        ));
    }

    @Operation(summary = "分页查询风控终止账号记录")
    @GetMapping("/terminations/risk")
    public AdminApiResponse<AccountRiskTerminationListResponse> listRiskTerminations(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return AdminApiResponse.ok(adminAccountManagementService.listRiskTerminations(
                userId,
                email,
                phone,
                page,
                pageSize
        ));
    }

    @Operation(summary = "查询风控终止账号详情")
    @GetMapping("/terminations/risk/{id}")
    public AdminApiResponse<AccountRiskTerminationDetailResponse> getRiskTerminationDetail(@PathVariable Long id) {
        return AdminApiResponse.ok(adminAccountManagementService.getRiskTerminationDetail(id));
    }

    private String currentAdmin(HttpServletRequest request) {
        AdminSessionMeResponse current = adminSessionService.current(request);
        String username = current == null ? "" : current.username();
        return username == null || username.isBlank() ? "admin" : username;
    }
}
