package com.example.ShoppingSystem.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
        return primaryDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "riskReadReplicaDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.risk-read.hikari")
    public HikariDataSource riskReadReplicaDataSource(RiskReadReplicaProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setReadOnly(true);
        dataSource.setPoolName("risk-read-replica");
        return dataSource;
    }

    @Bean(name = "couponReadReplicaDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.coupon-read.hikari")
    public HikariDataSource couponReadReplicaDataSource(CouponReadReplicaProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setReadOnly(true);
        dataSource.setPoolName("coupon-read-replica");
        return dataSource;
    }

    @Bean(name = "orderReadReplicaDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.order-read.hikari")
    public HikariDataSource orderReadReplicaDataSource(OrderReadReplicaProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setReadOnly(true);
        dataSource.setPoolName("order-read-replica");
        return dataSource;
    }

    @Bean(name = "productReadReplicaDataSource", destroyMethod = "close")
    @ConfigurationProperties("shopping.datasource.product-read.hikari")
    public HikariDataSource productReadReplicaDataSource(ProductReadReplicaProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setReadOnly(true);
        dataSource.setPoolName("product-read-replica");
        return dataSource;
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(@Qualifier("primaryDataSource") DataSource primaryDataSource,
                                 @Qualifier("riskReadReplicaDataSource") DataSource riskReadReplicaDataSource,
                                 @Qualifier("couponReadReplicaDataSource") DataSource couponReadReplicaDataSource,
                                 @Qualifier("orderReadReplicaDataSource") DataSource orderReadReplicaDataSource,
                                 @Qualifier("productReadReplicaDataSource") DataSource productReadReplicaDataSource,
                                 RiskReadReplicaProperties riskReadReplicaProperties,
                                 CouponReadReplicaProperties couponReadReplicaProperties,
                                 OrderReadReplicaProperties orderReadReplicaProperties,
                                 ProductReadReplicaProperties productReadReplicaProperties) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceRoute.PRIMARY, primaryDataSource);
        targetDataSources.put(
                DataSourceRoute.RISK_READ,
                riskReadReplicaProperties.isEnabled() ? riskReadReplicaDataSource : primaryDataSource);
        targetDataSources.put(
                DataSourceRoute.COUPON_READ,
                couponReadReplicaProperties.isEnabled() ? couponReadReplicaDataSource : primaryDataSource);
        targetDataSources.put(
                DataSourceRoute.ORDER_READ,
                orderReadReplicaProperties.isEnabled() ? orderReadReplicaDataSource : primaryDataSource);
        targetDataSources.put(
                DataSourceRoute.PRODUCT_READ,
                productReadReplicaProperties.isEnabled() ? productReadReplicaDataSource : primaryDataSource);

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }
}
