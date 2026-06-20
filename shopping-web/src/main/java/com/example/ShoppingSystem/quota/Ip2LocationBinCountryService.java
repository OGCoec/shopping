package com.example.ShoppingSystem.quota;

import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public interface Ip2LocationBinCountryService {
    public String queryCountryCode(String ip);

    public IpGeoSnapshot queryGeo(String ip);

    public void close();
}
