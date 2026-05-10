package com.example.ShoppingSystem.tools.ip2location.verify.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class MicrosoftImapAccessTokenClient {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftImapAccessTokenClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String tokenUrl;
    private final String imapScope;
    private final Duration requestTimeout;

    public MicrosoftImapAccessTokenClient(ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          String tokenUrl,
                                          String imapScope,
                                          Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.tokenUrl = tokenUrl;
        this.imapScope = imapScope;
        this.requestTimeout = requestTimeout;
    }

    public AccessTokenResult refresh(String clientId, String refreshToken) {
        String body = "client_id=" + urlEncode(clientId)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&scope=" + urlEncode(imapScope);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            JsonNode payload = readJson(response.body());
            if (statusCode < 200 || statusCode >= 300) {
                String error = text(payload, "error");
                String errorDescription = text(payload, "error_description");
                log.warn("IMAP token refresh failed, httpStatus={}, error={}, errorDescription={}",
                        statusCode, error, errorDescription);
                return AccessTokenResult.failed("token_http_status_" + statusCode);
            }

            String accessToken = text(payload, "access_token");
            if (isBlank(accessToken)) {
                return AccessTokenResult.failed("access_token_missing");
            }
            log.info("IMAP XOAUTH2 access token acquired, tokenLength={}", accessToken.length());
            return AccessTokenResult.succeeded(accessToken);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IMAP token refresh exception, reason={}", e.getMessage());
            return AccessTokenResult.failed("token_http_error");
        }
    }

    private JsonNode readJson(String raw) throws IOException {
        if (isBlank(raw)) {
            return null;
        }
        return objectMapper.readTree(raw);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return null;
        }
        return valueNode.asText();
    }

    private String urlEncode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record AccessTokenResult(boolean success, String reason, String accessToken) {
        private static AccessTokenResult succeeded(String accessToken) {
            return new AccessTokenResult(true, "ok", accessToken);
        }

        private static AccessTokenResult failed(String reason) {
            return new AccessTokenResult(false, reason, null);
        }
    }
}
