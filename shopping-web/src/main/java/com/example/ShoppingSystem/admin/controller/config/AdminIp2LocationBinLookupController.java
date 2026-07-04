package com.example.ShoppingSystem.admin.controller.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupResponse;
import com.example.ShoppingSystem.admin.service.ip2location.AdminIp2LocationBinLookupService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台IP2Location BIN查询", description = "后台IP2Location本地库查询接口")
@RestController
@RequestMapping("/shopping/admin/api/ip2location/bin")
public class AdminIp2LocationBinLookupController {

    private final AdminIp2LocationBinLookupService adminIp2LocationBinLookupService;

    public AdminIp2LocationBinLookupController(AdminIp2LocationBinLookupService adminIp2LocationBinLookupService) {
        this.adminIp2LocationBinLookupService = adminIp2LocationBinLookupService;
    }

    @Operation(summary = "执行IP2Location通配查询")
    @PostMapping("/wildcard-lookup")
    public AdminApiResponse<AdminIp2LocationBinLookupResponse> wildcardLookup(
            @RequestBody AdminIp2LocationBinLookupRequest request) {
        return AdminApiResponse.ok(adminIp2LocationBinLookupService.wildcardLookup(request));
    }
}
