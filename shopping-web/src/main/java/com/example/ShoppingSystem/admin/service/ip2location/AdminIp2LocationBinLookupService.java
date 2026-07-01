package com.example.ShoppingSystem.admin.service.ip2location;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupRequest;
import com.example.ShoppingSystem.admin.dto.AdminIp2LocationBinLookupResponse;
import com.ip2location.IP2Location;
public interface AdminIp2LocationBinLookupService {
    public AdminIp2LocationBinLookupResponse wildcardLookup(AdminIp2LocationBinLookupRequest request);

    public void close();
}
