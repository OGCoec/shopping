package com.example.ShoppingSystem.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopping.datasource.order-read")
public class OrderReadReplicaProperties {

    private boolean enabled = true;
    private boolean fallbackToPrimary = false;
    private String url = "jdbc:postgresql://127.0.0.1:5433/shopping_ip_read?ApplicationName=shopping-order-read";
    private String username = "shopping_readonly";
    private String password = "change_this_readonly_password";
    private String driverClassName = "org.postgresql.Driver";

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
}
