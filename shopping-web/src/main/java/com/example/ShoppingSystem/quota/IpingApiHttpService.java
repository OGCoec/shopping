package com.example.ShoppingSystem.quota;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface IpingApiHttpService {
    public record IpingQueryResult(boolean success,
                                       String reason,
                                       int httpStatus,
                                       JsonNode payload,
                                       Ip2LocationQuotaHttpService.RiskRelevantFields riskFields) {
            public static IpingQueryResult succeeded(int httpStatus,
                                                     JsonNode payload,
                                                     Ip2LocationQuotaHttpService.RiskRelevantFields riskFields) {
                return new IpingQueryResult(true, "ok", httpStatus, payload, riskFields);
            }

            public static IpingQueryResult failed(String reason, int httpStatus) {
                return new IpingQueryResult(false, reason, httpStatus, null, null);
            }
        }

    public IpingQueryResult queryByIp(String ip);
}
