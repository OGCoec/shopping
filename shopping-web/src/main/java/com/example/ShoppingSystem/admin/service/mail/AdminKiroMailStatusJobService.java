package com.example.ShoppingSystem.admin.service.mail;

import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusCheckRequest;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobCreateResponse;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusJobResponse;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusResultItem;
import com.example.ShoppingSystem.admin.dto.AdminKiroMailStatusSummary;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.example.ShoppingSystem.tools.ip2location.verify.model.MailCredentials;
import com.example.ShoppingSystem.tools.kiro.mail.KiroMailStatusReaderService;
import com.example.ShoppingSystem.tools.kiro.mail.KiroMailStatusReaderService.KiroMailStatusScanResult;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public interface AdminKiroMailStatusJobService {
    public AdminKiroMailStatusJobCreateResponse createJob(AdminKiroMailStatusCheckRequest request);

    public AdminKiroMailStatusJobResponse getJob(String jobId);

    public void shutdown();
}
