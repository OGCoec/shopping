package com.example.ShoppingSystem.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "shopping.datasource.route-diagnostics")
public class SqlRouteDiagnosticsProperties {

    private boolean enabled;
    private boolean logSql = true;
    private int maxSqlLength = 500;
    private boolean logJdbcUrl = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogSql() {
        return logSql;
    }

    public void setLogSql(boolean logSql) {
        this.logSql = logSql;
    }

    public int getMaxSqlLength() {
        return maxSqlLength;
    }

    public void setMaxSqlLength(int maxSqlLength) {
        this.maxSqlLength = maxSqlLength;
    }

    public boolean isLogJdbcUrl() {
        return logJdbcUrl;
    }

    public void setLogJdbcUrl(boolean logJdbcUrl) {
        this.logJdbcUrl = logJdbcUrl;
    }
}
