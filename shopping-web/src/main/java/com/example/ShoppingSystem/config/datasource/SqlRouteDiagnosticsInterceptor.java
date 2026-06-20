package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.RoutingDataSourceContext;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class SqlRouteDiagnosticsInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlRouteDiagnosticsInterceptor.class);
    private static final String UNKNOWN = "unknown";

    private final SqlRouteDiagnosticsProperties properties;

    public SqlRouteDiagnosticsInterceptor(SqlRouteDiagnosticsProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (properties.isEnabled() && log.isInfoEnabled()) {
            logRoute(invocation);
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private void logRoute(Invocation invocation) {
        try {
            Connection connection = (Connection) invocation.getArgs()[0];
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            String jdbcUrl = readJdbcUrl(connection);
            JdbcTarget jdbcTarget = JdbcTarget.parse(jdbcUrl);
            String rawMappedStatementId = resolveMappedStatementId(statementHandler);
            String mappedStatementId = shortMappedStatementId(rawMappedStatementId);
            DataSourceRoute expectedRoute = expectedRoute(rawMappedStatementId);
            String sql = properties.isLogSql() ? summarizeSql(statementHandler.getBoundSql()) : "<disabled>";
            if (properties.isLogJdbcUrl()) {
                log.info(
                        "[SqlRoute] routeContext={}, expectedRoute={}, jdbcPort={}, database={}, jdbcUrl={}, mapper={}, sql={}",
                        RoutingDataSourceContext.current(),
                        expectedRoute,
                        jdbcTarget.port(),
                        jdbcTarget.database(),
                        jdbcUrl,
                        mappedStatementId,
                        sql
                );
                return;
            }
            log.info(
                    "[SqlRoute] routeContext={}, expectedRoute={}, jdbcPort={}, database={}, mapper={}, sql={}",
                    RoutingDataSourceContext.current(),
                    expectedRoute,
                    jdbcTarget.port(),
                    jdbcTarget.database(),
                    mappedStatementId,
                    sql
            );
        } catch (RuntimeException | SQLException e) {
            log.warn("[SqlRoute] diagnostics failed, reason={}", e.getMessage());
        }
    }

    private String readJdbcUrl(Connection connection) throws SQLException {
        String url = connection.getMetaData().getURL();
        return url == null || url.isBlank() ? UNKNOWN : url;
    }

    private String resolveMappedStatementId(StatementHandler statementHandler) {
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        Object mappedStatement = readMetaValue(metaObject, "delegate.mappedStatement");
        if (mappedStatement == null) {
            mappedStatement = readMetaValue(metaObject, "mappedStatement");
        }
        if (mappedStatement instanceof MappedStatement statement) {
            return statement.getId();
        }
        return UNKNOWN;
    }

    private Object readMetaValue(MetaObject metaObject, String path) {
        try {
            if (metaObject.hasGetter(path)) {
                return metaObject.getValue(path);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private String shortMappedStatementId(String mappedStatementId) {
        if (mappedStatementId == null || mappedStatementId.isBlank() || UNKNOWN.equals(mappedStatementId)) {
            return UNKNOWN;
        }
        int methodSeparator = mappedStatementId.lastIndexOf('.');
        if (methodSeparator < 0) {
            return mappedStatementId;
        }
        int classSeparator = mappedStatementId.lastIndexOf('.', methodSeparator - 1);
        if (classSeparator < 0) {
            return mappedStatementId;
        }
        return mappedStatementId.substring(classSeparator + 1);
    }

    private DataSourceRoute expectedRoute(String mappedStatementId) {
        if (mappedStatementId == null || mappedStatementId.isBlank() || UNKNOWN.equals(mappedStatementId)) {
            return null;
        }
        if (mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.user.")) {
            return DataSourceRoute.CORE;
        }
        if (mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listSelfTerminations")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findSelfTerminationById")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.restoreDisabledIdentity")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.markSelfTerminationRestored")) {
            return DataSourceRoute.CORE;
        }
        if (mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listAccountCreditProfiles")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findAccountCreditDetail")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findFirstLoginRecord")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listRiskScoreEvents")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listRecentRiskScoreEvents")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.lockRiskProfileForAdjust")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.updateRiskProfileScore")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.insertRiskScoreEvent")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listRiskTerminations")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findRiskTerminationById")) {
            return DataSourceRoute.RISK;
        }
        if (mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper.")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper.")) {
            return DataSourceRoute.COUPON;
        }
        if (mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.coupon.UserCouponMapper.")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.coupon.CouponUsageRecordMapper.")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.order.")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.signin.")
                || mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.product.CardSecretInventoryMapper.")) {
            return DataSourceRoute.TRADE;
        }
        if (mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.product.")) {
            return DataSourceRoute.PRODUCT;
        }
        if (mappedStatementId.startsWith("com.example.ShoppingSystem.mapper.risk.")) {
            return DataSourceRoute.RISK;
        }
        return null;
    }

    private String summarizeSql(BoundSql boundSql) {
        String sql = boundSql == null ? "" : boundSql.getSql();
        String normalized = sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return UNKNOWN;
        }
        int maxLength = Math.max(properties.getMaxSqlLength(), 0);
        if (maxLength == 0 || normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 3) {
            return normalized.substring(0, maxLength);
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private record JdbcTarget(String port, String database) {

        static JdbcTarget parse(String jdbcUrl) {
            if (jdbcUrl == null || jdbcUrl.isBlank() || UNKNOWN.equals(jdbcUrl)) {
                return new JdbcTarget(UNKNOWN, UNKNOWN);
            }
            String remaining = jdbcUrl;
            int schemeSeparator = remaining.indexOf("://");
            if (schemeSeparator >= 0) {
                remaining = remaining.substring(schemeSeparator + 3);
            }
            int slash = remaining.indexOf('/');
            String authority = slash >= 0 ? remaining.substring(0, slash) : remaining;
            String database = slash >= 0 ? remaining.substring(slash + 1) : UNKNOWN;
            int query = database.indexOf('?');
            if (query >= 0) {
                database = database.substring(0, query);
            }
            String firstHost = authority.split(",", 2)[0];
            String port = UNKNOWN;
            int bracketPort = firstHost.lastIndexOf("]:");
            int colon = bracketPort >= 0 ? bracketPort + 1 : firstHost.lastIndexOf(':');
            if (colon >= 0 && colon + 1 < firstHost.length()) {
                port = firstHost.substring(colon + 1);
            }
            if (database.isBlank()) {
                database = UNKNOWN;
            }
            return new JdbcTarget(port, database);
        }
    }
}
