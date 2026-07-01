package com.example.ShoppingSystem.admin.service.mail;

import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusCheckRequest;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobCreateResponse;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobResponse;
public interface AdminKiroMailStatusJobService {
    public AdminKiroMailStatusJobCreateResponse createJob(AdminKiroMailStatusCheckRequest request);

    public AdminKiroMailStatusJobResponse getJob(String jobId);

    public void shutdown();
}
