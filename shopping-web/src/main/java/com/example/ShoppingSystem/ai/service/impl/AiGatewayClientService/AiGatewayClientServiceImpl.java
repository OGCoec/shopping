package com.example.ShoppingSystem.ai.service.impl.AiGatewayClientService;

import com.example.ShoppingSystem.ai.config.AiProperties;
import com.example.ShoppingSystem.ai.dto.AiChatMessage;
import com.example.ShoppingSystem.ai.service.AiGatewayClientService;
import com.example.ShoppingSystem.ai.service.AiGatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AiGatewayClientServiceImpl implements AiGatewayClientService {

    private final AiProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiGatewayClientServiceImpl(AiProperties properties,
                                      HttpClient aiHttpClient,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = aiHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(AiProperties.Model model,
                           List<AiChatMessage> messages,
                           double temperature,
                           int maxTokens) {
        try {
            String body = objectMapper.writeValueAsString(payload(model, messages, temperature, maxTokens, false));
            HttpRequest request = baseRequest()
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiGatewayException("AI_GATEWAY_HTTP_" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() || content.isNull() ? "" : content.asText("");
        } catch (AiGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new AiGatewayException("AI_GATEWAY_UNAVAILABLE", e);
        }
    }

    @Override
    public void stream(AiProperties.Model model,
                       List<AiChatMessage> messages,
                       double temperature,
                       Consumer<String> chunkConsumer) {
        try {
            String body = objectMapper.writeValueAsString(payload(model, messages, temperature, 0, true));
            HttpRequest request = baseRequest()
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiGatewayException("AI_GATEWAY_HTTP_" + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleStreamLine(line, chunkConsumer);
                }
            }
        } catch (AiGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new AiGatewayException("AI_GATEWAY_UNAVAILABLE", e);
        }
    }

    private HttpRequest.Builder baseRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.completionUrl()))
                .timeout(Duration.ofMillis(Math.max(1000, properties.getGatewayTimeoutMillis())))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        String apiKey = properties.getApiKey() == null ? "" : properties.getApiKey().trim();
        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private Map<String, Object> payload(AiProperties.Model model,
                                        List<AiChatMessage> messages,
                                        double temperature,
                                        int maxTokens,
                                        boolean stream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", providerModelName(model));
        payload.put("temperature", temperature);
        payload.put("stream", stream);
        if (maxTokens > 0) {
            payload.put("max_tokens", maxTokens);
        }
        payload.put("messages", messages.stream()
                .map(message -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("role", normalizeRole(message == null ? null : message.role()));
                    item.put("content", message == null || message.content() == null ? "" : message.content());
                    return item;
                })
                .toList());
        return payload;
    }

    private void handleStreamLine(String rawLine, Consumer<String> chunkConsumer) throws Exception {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || !line.startsWith("data:")) {
            return;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }
        JsonNode root = objectMapper.readTree(data);
        JsonNode choice = root.path("choices").path(0);
        JsonNode content = choice.path("delta").path("content");
        if (content.isMissingNode() || content.isNull()) {
            content = choice.path("message").path("content");
        }
        String text = content.isMissingNode() || content.isNull() ? "" : content.asText("");
        if (!text.isEmpty()) {
            chunkConsumer.accept(text);
        }
    }

    private String providerModelName(AiProperties.Model model) {
        String providerName = model == null ? "" : model.getProviderModelName();
        if (providerName != null && !providerName.isBlank()) {
            return providerName.trim();
        }
        return model == null ? "" : model.getModelKey();
    }

    private String normalizeRole(String rawRole) {
        String role = rawRole == null ? "" : rawRole.trim().toLowerCase();
        return switch (role) {
            case "system", "user", "assistant" -> role;
            default -> "user";
        };
    }
}
