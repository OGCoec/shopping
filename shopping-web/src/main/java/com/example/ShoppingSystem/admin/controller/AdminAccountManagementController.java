package com.example.ShoppingSystem.admin.controller;

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
import com.example.ShoppingSystem.admin.service.AdminAccountManagementService;
import com.example.ShoppingSystem.admin.service.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/credit/{userId}")
    public AdminApiResponse<AccountCreditDetailResponse> getAccountCreditDetail(@PathVariable Long userId) {
        return AdminApiResponse.ok(adminAccountManagementService.getAccountCreditDetail(userId));
    }

    @GetMapping("/credit/{userId}/events")
    public AdminApiResponse<AccountRiskScoreEventListResponse> listAccountCreditEvents(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return AdminApiResponse.ok(adminAccountManagementService.listAccountCreditEvents(userId, page, pageSize));
    }

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
