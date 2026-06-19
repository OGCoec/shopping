package com.example.ShoppingSystem.config.datasource;

import java.util.ArrayList;
import java.util.List;

public class ReadReplicaGroupProperties {

    private boolean enabled = false;
    private boolean fallbackToPrimary = true;
    private String url;
    private String username = "postgres";
    private String password = "123456";
    private String driverClassName = "org.postgresql.Driver";
    private List<ReadReplicaDataSourceProperties> replicas = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFallbackToPrimary() {
        return fallbackToPrimary;
    }

    public void setFallbackToPrimary(boolean fallbackToPrimary) {
        this.fallbackToPrimary = fallbackToPrimary;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public List<ReadReplicaDataSourceProperties> getReplicas() {
        return replicas;
    }

    public void setReplicas(List<ReadReplicaDataSourceProperties> replicas) {
        this.replicas = replicas == null ? new ArrayList<>() : replicas;
    }

    public ReadReplicaDataSourceProperties resolvedReplica(int index) {
        ReadReplicaDataSourceProperties source = index >= 0 && index < replicas.size()
                ? replicas.get(index)
                : null;
        ReadReplicaDataSourceProperties resolved = new ReadReplicaDataSourceProperties();
        resolved.setUrl(firstText(source == null ? null : source.getUrl(), url));
        resolved.setUsername(firstText(source == null ? null : source.getUsername(), username));
        resolved.setPassword(firstText(source == null ? null : source.getPassword(), password));
        resolved.setDriverClassName(firstText(source == null ? null : source.getDriverClassName(), driverClassName));
        return resolved;
    }

    protected void defaultReplicaUrls(String firstUrl, String secondUrl) {
        this.url = firstUrl;
        this.replicas = new ArrayList<>(List.of(
                new ReadReplicaDataSourceProperties(firstUrl),
                new ReadReplicaDataSourceProperties(secondUrl)
        ));
    }

    private String firstText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
