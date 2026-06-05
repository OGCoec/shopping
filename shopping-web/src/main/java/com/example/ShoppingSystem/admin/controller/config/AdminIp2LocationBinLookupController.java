package com.example.ShoppingSystem.admin.controller.config;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupResponse;
import com.example.ShoppingSystem.admin.service.ip2location.AdminIp2LocationBinLookupService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shopping/admin/api/ip2location/bin")
public class AdminIp2LocationBinLookupController {

    private final AdminIp2LocationBinLookupService adminIp2LocationBinLookupService;

    public AdminIp2LocationBinLookupController(AdminIp2LocationBinLookupService adminIp2LocationBinLookupService) {
        this.adminIp2LocationBinLookupService = adminIp2LocationBinLookupService;
    }

    @PostMapping("/wildcard-lookup")
    public AdminApiResponse<AdminIp2LocationBinLookupResponse> wildcardLookup(
            @RequestBody AdminIp2LocationBinLookupRequest request) {
        return AdminApiResponse.ok(adminIp2LocationBinLookupService.wildcardLookup(request));
    }
}
