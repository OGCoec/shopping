package com.example.ShoppingSystem.admin.service.account;
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
public interface AdminAccountManagementService {
    public AccountCreditListResponse listAccountCredits(Long userId,
                                                        String email,
                                                        String phone,
                                                        String status,
                                                        String riskLevel,
                                                        int page,
                                                        int pageSize);

    public AccountCreditDetailResponse getAccountCreditDetail(Long userId);

    public AccountRiskScoreEventListResponse listAccountCreditEvents(Long userId, int page, int pageSize);

    public AccountScoreAdjustResponse adjustAccountScore(Long userId,
                                                         AccountScoreAdjustRequest request,
                                                         String adminUsername);

    public AccountSelfTerminationListResponse listSelfTerminations(String scope,
                                                                   Long userId,
                                                                   String email,
                                                                   String phone,
                                                                   int page,
                                                                   int pageSize);

    public AccountRestoreResponse restoreSelfTermination(Long id,
                                                         AccountRestoreRequest request,
                                                         String adminUsername);

    public AccountRiskTerminationListResponse listRiskTerminations(Long userId,
                                                                   String email,
                                                                   String phone,
                                                                   int page,
                                                                   int pageSize);

    public AccountRiskTerminationDetailResponse getRiskTerminationDetail(Long id);
}
