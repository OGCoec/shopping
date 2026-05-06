package com.example.ShoppingSystem.service.user.auth.risk;

/**
 * Optional geo lookup bridge used by the service module without depending on web classes.
 */
public interface DeviceIpGeoLookupService {

    DeviceIpGeoSnapshot queryGeo(String publicIp);
}
