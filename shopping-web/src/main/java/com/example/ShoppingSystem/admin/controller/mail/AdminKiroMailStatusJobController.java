package com.example.ShoppingSystem.admin.controller.mail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusCheckRequest;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobCreateResponse;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobResponse;
import com.example.ShoppingSystem.admin.service.mail.AdminKiroMailStatusJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台Kiro邮件状态", description = "后台Kiro邮件状态检查任务接口")
@RestController
@RequestMapping("/shopping/admin/api/mail/kiro")
public class AdminKiroMailStatusJobController {

    private final AdminKiroMailStatusJobService jobService;

    public AdminKiroMailStatusJobController(AdminKiroMailStatusJobService jobService) {
        this.jobService = jobService;
    }

    @Operation(summary = "创建Kiro邮件状态检查任务")
    @PostMapping("/status-check-jobs")
    public AdminApiResponse<AdminKiroMailStatusJobCreateResponse> createStatusCheckJob(
            @RequestBody AdminKiroMailStatusCheckRequest request) {
        return AdminApiResponse.ok(jobService.createJob(request));
    }

    @Operation(summary = "查询Kiro邮件状态检查任务")
    @GetMapping("/status-check-jobs/{jobId}")
    public AdminApiResponse<AdminKiroMailStatusJobResponse> getStatusCheckJob(@PathVariable String jobId) {
        return AdminApiResponse.ok(jobService.getJob(jobId));
    }
}
