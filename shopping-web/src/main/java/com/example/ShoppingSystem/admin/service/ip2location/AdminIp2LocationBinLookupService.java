package com.example.ShoppingSystem.admin.service.ip2location;

import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupItemResponse;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupResponse;
import com.example.ShoppingSystem.admin.service.common.AdminPaginationValidator;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public interface AdminIp2LocationBinLookupService {
    public AdminIp2LocationBinLookupResponse wildcardLookup(AdminIp2LocationBinLookupRequest request);

    public void close();
}
