package com.example.ShoppingSystem.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String baseUrl = "http://127.0.0.1:8317/v1";
    private String apiKey = "your-api-key-1";
    private String completionsPath = "/chat/completions";
    private String defaultModel = "kiro-haiku-4.5";
    private int contextHardLimitTokens = 64000;
    private int compressionTriggerTokens = 52000;
    private int recentMessageRetainCount = 20;
    private int streamTimeoutMillis = 180000;
    private int gatewayTimeoutMillis = 120000;
    private List<Model> models = new ArrayList<>(List.of(
            new Model("kiro-haiku-4.5", "kiro-haiku-4.5", "Kiro Haiku 4.5", true, 128000, 12000, 0.40),
            new Model("kiro-minimax-m2.1", "kiro-minimax-m2.1", "Kiro MiniMax M2.1", true, 128000, 12000, 0.15)
    ));

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCompletionsPath() {
        return completionsPath;
    }

    public void setCompletionsPath(String completionsPath) {
        this.completionsPath = completionsPath;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public int getContextHardLimitTokens() {
        return contextHardLimitTokens;
    }

    public void setContextHardLimitTokens(int contextHardLimitTokens) {
        this.contextHardLimitTokens = contextHardLimitTokens;
    }

    public int getCompressionTriggerTokens() {
        return compressionTriggerTokens;
    }

    public void setCompressionTriggerTokens(int compressionTriggerTokens) {
        this.compressionTriggerTokens = compressionTriggerTokens;
    }

    public int getRecentMessageRetainCount() {
        return recentMessageRetainCount;
    }

    public void setRecentMessageRetainCount(int recentMessageRetainCount) {
        this.recentMessageRetainCount = recentMessageRetainCount;
    }

    public int getStreamTimeoutMillis() {
        return streamTimeoutMillis;
    }

    public void setStreamTimeoutMillis(int streamTimeoutMillis) {
        this.streamTimeoutMillis = streamTimeoutMillis;
    }

    public int getGatewayTimeoutMillis() {
        return gatewayTimeoutMillis;
    }

    public void setGatewayTimeoutMillis(int gatewayTimeoutMillis) {
        this.gatewayTimeoutMillis = gatewayTimeoutMillis;
    }

    public List<Model> getModels() {
        return models;
    }

    public void setModels(List<Model> models) {
        this.models = models == null ? new ArrayList<>() : models;
    }

    public Optional<Model> findEnabledModel(String rawModelKey) {
        String modelKey = rawModelKey == null || rawModelKey.isBlank() ? defaultModel : rawModelKey.trim();
        return models.stream()
                .filter(Model::isEnabled)
                .filter(model -> modelKey.equals(model.getModelKey()))
                .findFirst();
    }

    public List<Model> enabledModels() {
        return models.stream()
                .filter(Model::isEnabled)
                .toList();
    }

    public String completionUrl() {
        String normalizedBaseUrl = trimRight(baseUrl == null ? "" : baseUrl.trim(), "/");
        String normalizedPath = completionsPath == null || completionsPath.isBlank()
                ? "/chat/completions"
                : completionsPath.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedBaseUrl + normalizedPath;
    }

    private String trimRight(String value, String suffix) {
        String result = value;
        while (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }

    public static class Model {
        private String modelKey;
        private String providerModelName;
        private String displayName;
        private boolean enabled;
        private int contextTokens;
        private int outputReserveTokens;
        private double temperature;

        public Model() {
        }

        public Model(String modelKey,
                     String providerModelName,
                     String displayName,
                     boolean enabled,
                     int contextTokens,
                     int outputReserveTokens,
                     double temperature) {
            this.modelKey = modelKey;
            this.providerModelName = providerModelName;
            this.displayName = displayName;
            this.enabled = enabled;
            this.contextTokens = contextTokens;
            this.outputReserveTokens = outputReserveTokens;
            this.temperature = temperature;
        }

        public String getModelKey() {
            return modelKey;
        }

        public void setModelKey(String modelKey) {
            this.modelKey = modelKey;
        }

        public String getProviderModelName() {
            return providerModelName;
        }

        public void setProviderModelName(String providerModelName) {
            this.providerModelName = providerModelName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getContextTokens() {
            return contextTokens;
        }

        public void setContextTokens(int contextTokens) {
            this.contextTokens = contextTokens;
        }

        public int getOutputReserveTokens() {
            return outputReserveTokens;
        }

        public void setOutputReserveTokens(int outputReserveTokens) {
            this.outputReserveTokens = outputReserveTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }
}
