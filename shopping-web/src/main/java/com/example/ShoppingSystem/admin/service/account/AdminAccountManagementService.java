package com.example.ShoppingSystem.admin.service.account;

import com.example.ShoppingSystem.Utils.SnowflakeIdWorker;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditListItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountCreditListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountLoginRecordResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRestoreRequest;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRestoreResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskScoreEventListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskScoreEventResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationDetailResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountRiskTerminationListResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountScoreAdjustRequest;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountScoreAdjustResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountSelfTerminationItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminAccountManagementDtos.AccountSelfTerminationListResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.postgresql.util.PGobject;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;

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
