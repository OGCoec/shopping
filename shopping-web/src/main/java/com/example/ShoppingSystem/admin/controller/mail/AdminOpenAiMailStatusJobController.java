package com.example.ShoppingSystem.admin.controller.mail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.dto.AdminOpenAiMailStatusCheckRequest;
import com.example.ShoppingSystem.admin.dto.AdminOpenAiMailStatusJobCreateResponse;
import com.example.ShoppingSystem.admin.dto.AdminOpenAiMailStatusJobResponse;
import com.example.ShoppingSystem.admin.service.mail.AdminOpenAiMailStatusJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台OpenAI邮件状态", description = "后台OpenAI邮件状态检查任务接口")
@RestController
@RequestMapping("/shopping/admin/api/mail/openai")
public class AdminOpenAiMailStatusJobController {

    private final AdminOpenAiMailStatusJobService jobService;

    public AdminOpenAiMailStatusJobController(AdminOpenAiMailStatusJobService jobService) {
        this.jobService = jobService;
    }

    @Operation(summary = "创建OpenAI邮件状态检查任务")
    @PostMapping("/status-check-jobs")
    public AdminApiResponse<AdminOpenAiMailStatusJobCreateResponse> createStatusCheckJob(
            @RequestBody AdminOpenAiMailStatusCheckRequest request) {
        return AdminApiResponse.ok(jobService.createJob(request));
    }

    @Operation(summary = "查询OpenAI邮件状态检查任务")
    @GetMapping("/status-check-jobs/{jobId}")
    public AdminApiResponse<AdminOpenAiMailStatusJobResponse> getStatusCheckJob(@PathVariable String jobId) {
        return AdminApiResponse.ok(jobService.getJob(jobId));
    }
}
