package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        RiskReadReplicaProperties.class,
        CouponReadReplicaProperties.class,
        OrderReadReplicaProperties.class,
        ProductReadReplicaProperties.class
})
public class RoutingDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "primaryDataSource", destroyMethod = "close")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource primaryDataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties primaryDataSourceProperties) {
        HikariDataSource dataSource = primaryDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("core-primary");
        return dataSource;
    }

    @Bean
    @ConfigurationProperties("shopping.datasource.trade")
    public ShardDataSourceProperties tradeDataSourceProperties() {
        return new ShardDataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("shopping.datasource.product")
    public ShardDataSourceProperties productDataSourceProperties() {
        return new ShardDataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("shopping.datasource.coupon")
    public ShardDataSourceProperties couponDataSourceProperties() {
        return new ShardDataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("shopping.datasource.risk")
    public ShardDataSourceProperties riskDataSourceProperties() {
        return new ShardDataSourceProperties();
    }

    @Bean(name = "tradeDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.trade.hikari")
    public HikariDataSource tradeDataSource(
            @Qualifier("tradeDataSourceProperties") ShardDataSourceProperties properties) {
        return writableDataSource(properties, "trade-primary");
    }

    @Bean(name = "productDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.product.hikari")
    public HikariDataSource productDataSource(
            @Qualifier("productDataSourceProperties") ShardDataSourceProperties properties) {
        return writableDataSource(properties, "product-primary");
    }

    @Bean(name = "couponDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.coupon.hikari")
    public HikariDataSource couponDataSource(
            @Qualifier("couponDataSourceProperties") ShardDataSourceProperties properties) {
        return writableDataSource(properties, "coupon-primary");
    }

    @Bean(name = "riskDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.risk.hikari")
    public HikariDataSource riskDataSource(
            @Qualifier("riskDataSourceProperties") ShardDataSourceProperties properties) {
        return writableDataSource(properties, "risk-primary");
    }

    @Bean(name = "riskReadReplica1DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.risk-read.hikari")
    public HikariDataSource riskReadReplica1DataSource(RiskReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(0), "risk-read-replica-1");
    }

    @Bean(name = "riskReadReplica2DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.risk-read.hikari")
    public HikariDataSource riskReadReplica2DataSource(RiskReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(1), "risk-read-replica-2");
    }

    @Bean(name = "couponReadReplica1DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.coupon-read.hikari")
    public HikariDataSource couponReadReplica1DataSource(CouponReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(0), "coupon-read-replica-1");
    }

    @Bean(name = "couponReadReplica2DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.coupon-read.hikari")
    public HikariDataSource couponReadReplica2DataSource(CouponReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(1), "coupon-read-replica-2");
    }

    @Bean(name = "orderReadReplica1DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.order-read.hikari")
    public HikariDataSource orderReadReplica1DataSource(OrderReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(0), "order-read-replica-1");
    }

    @Bean(name = "orderReadReplica2DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.order-read.hikari")
    public HikariDataSource orderReadReplica2DataSource(OrderReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(1), "order-read-replica-2");
    }

    @Bean(name = "productReadReplica1DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.product-read.hikari")
    public HikariDataSource productReadReplica1DataSource(ProductReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(0), "product-read-replica-1");
    }

    @Bean(name = "productReadReplica2DataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.product-read.hikari")
    public HikariDataSource productReadReplica2DataSource(ProductReadReplicaProperties properties) {
        return readOnlyDataSource(properties.resolvedReplica(1), "product-read-replica-2");
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(@Qualifier("primaryDataSource") DataSource primaryDataSource,
                                 @Qualifier("tradeDataSource") DataSource tradeDataSource,
                                 @Qualifier("productDataSource") DataSource productDataSource,
                                 @Qualifier("couponDataSource") DataSource couponDataSource,
                                 @Qualifier("riskDataSource") DataSource riskDataSource,
                                 @Qualifier("riskReadReplica1DataSource") DataSource riskReadReplica1DataSource,
                                 @Qualifier("riskReadReplica2DataSource") DataSource riskReadReplica2DataSource,
                                 @Qualifier("couponReadReplica1DataSource") DataSource couponReadReplica1DataSource,
                                 @Qualifier("couponReadReplica2DataSource") DataSource couponReadReplica2DataSource,
                                 @Qualifier("orderReadReplica1DataSource") DataSource orderReadReplica1DataSource,
                                 @Qualifier("orderReadReplica2DataSource") DataSource orderReadReplica2DataSource,
                                 @Qualifier("productReadReplica1DataSource") DataSource productReadReplica1DataSource,
                                 @Qualifier("productReadReplica2DataSource") DataSource productReadReplica2DataSource,
                                 RiskReadReplicaProperties riskReadReplicaProperties,
                                 CouponReadReplicaProperties couponReadReplicaProperties,
                                 OrderReadReplicaProperties orderReadReplicaProperties,
                                 ProductReadReplicaProperties productReadReplicaProperties) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceRoute.PRIMARY, primaryDataSource);
        targetDataSources.put(DataSourceRoute.CORE, primaryDataSource);
        targetDataSources.put(DataSourceRoute.TRADE, tradeDataSource);
        targetDataSources.put(DataSourceRoute.PRODUCT, productDataSource);
        targetDataSources.put(DataSourceRoute.COUPON, couponDataSource);
        targetDataSources.put(DataSourceRoute.RISK, riskDataSource);
        targetDataSources.put(
                DataSourceRoute.RISK_READ,
                riskReadReplicaProperties.isEnabled() ? riskReadReplica1DataSource : riskDataSource);
        targetDataSources.put(
                DataSourceRoute.RISK_READ_1,
                riskReadReplicaProperties.isEnabled() ? riskReadReplica1DataSource : riskDataSource);
        targetDataSources.put(
                DataSourceRoute.RISK_READ_2,
                riskReadReplicaProperties.isEnabled() ? riskReadReplica2DataSource : riskDataSource);
        targetDataSources.put(
                DataSourceRoute.COUPON_READ,
                couponReadReplicaProperties.isEnabled() ? couponReadReplica1DataSource : couponDataSource);
        targetDataSources.put(
                DataSourceRoute.COUPON_READ_1,
                couponReadReplicaProperties.isEnabled() ? couponReadReplica1DataSource : couponDataSource);
        targetDataSources.put(
                DataSourceRoute.COUPON_READ_2,
                couponReadReplicaProperties.isEnabled() ? couponReadReplica2DataSource : couponDataSource);
        targetDataSources.put(
                DataSourceRoute.ORDER_READ,
                orderReadReplicaProperties.isEnabled() ? orderReadReplica1DataSource : tradeDataSource);
        targetDataSources.put(
                DataSourceRoute.ORDER_READ_1,
                orderReadReplicaProperties.isEnabled() ? orderReadReplica1DataSource : tradeDataSource);
        targetDataSources.put(
                DataSourceRoute.ORDER_READ_2,
                orderReadReplicaProperties.isEnabled() ? orderReadReplica2DataSource : tradeDataSource);
        targetDataSources.put(
                DataSourceRoute.PRODUCT_READ,
                productReadReplicaProperties.isEnabled() ? productReadReplica1DataSource : productDataSource);
        targetDataSources.put(
                DataSourceRoute.PRODUCT_READ_1,
                productReadReplicaProperties.isEnabled() ? productReadReplica1DataSource : productDataSource);
        targetDataSources.put(
                DataSourceRoute.PRODUCT_READ_2,
                productReadReplicaProperties.isEnabled() ? productReadReplica2DataSource : productDataSource);

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    private HikariDataSource writableDataSource(ShardDataSourceProperties properties, String poolName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setPoolName(poolName);
        return dataSource;
    }

    private HikariDataSource readOnlyDataSource(ReadReplicaDataSourceProperties properties, String poolName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setReadOnly(true);
        dataSource.setPoolName(poolName);
        return dataSource;
    }
}
