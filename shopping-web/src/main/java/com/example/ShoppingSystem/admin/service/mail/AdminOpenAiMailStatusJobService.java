package com.example.ShoppingSystem.admin.service.mail;

import com.example.ShoppingSystem.admin.dto.AdminOpenAiMailStatusCheckRequest;
import com.example.ShoppingSystem.admin.dto.AdminOpenAiMailStatusJobCreateResponse;
import com.example.ShoppingSystem.admin.dto.AdminOpenAiMailStatusJobResponse;
public interface AdminOpenAiMailStatusJobService {
    public AdminOpenAiMailStatusJobCreateResponse createJob(AdminOpenAiMailStatusCheckRequest request);

    public AdminOpenAiMailStatusJobResponse getJob(String jobId);

    public void shutdown();
}
