package com.example.ShoppingSystem.admin.controller.mail;

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

@RestController
@RequestMapping("/shopping/admin/api/mail/openai")
public class AdminOpenAiMailStatusJobController {

    private final AdminOpenAiMailStatusJobService jobService;

    public AdminOpenAiMailStatusJobController(AdminOpenAiMailStatusJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/status-check-jobs")
    public AdminApiResponse<AdminOpenAiMailStatusJobCreateResponse> createStatusCheckJob(
            @RequestBody AdminOpenAiMailStatusCheckRequest request) {
        return AdminApiResponse.ok(jobService.createJob(request));
    }

    @GetMapping("/status-check-jobs/{jobId}")
    public AdminApiResponse<AdminOpenAiMailStatusJobResponse> getStatusCheckJob(@PathVariable String jobId) {
        return AdminApiResponse.ok(jobService.getJob(jobId));
    }
}
