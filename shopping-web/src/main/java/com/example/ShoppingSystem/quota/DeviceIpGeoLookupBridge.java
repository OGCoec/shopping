package com.example.ShoppingSystem.quota;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceIpGeoLookupService;
import com.example.ShoppingSystem.service.user.auth.risk.DeviceIpGeoSnapshot;
import org.springframework.stereotype.Service;

/**
 * Bridges the web geo cache chain into the service-layer device risk writer.
 */
@Service
public class DeviceIpGeoLookupBridge implements DeviceIpGeoLookupService {

    private final IpCountryQueryService ipCountryQueryService;

    public DeviceIpGeoLookupBridge(IpCountryQueryService ipCountryQueryService) {
        this.ipCountryQueryService = ipCountryQueryService;
    }

    @Override
    public DeviceIpGeoSnapshot queryGeo(String publicIp) {
        if (StrUtil.isBlank(publicIp)) {
            return null;
        }
        try {
            IpCountryQueryService.GeoQueryResult result = ipCountryQueryService.queryGeo(publicIp);
            if (result == null || !result.success() || result.geo() == null || !result.geo().hasAnyGeo()) {
                return null;
            }
            IpGeoSnapshot geo = result.geo();
            return new DeviceIpGeoSnapshot(
                    geo.country(),
                    geo.region(),
                    geo.city(),
                    geo.latitude(),
                    geo.longitude()
            );
        } catch (Exception ignored) {
            return null;
        }
    }
}
