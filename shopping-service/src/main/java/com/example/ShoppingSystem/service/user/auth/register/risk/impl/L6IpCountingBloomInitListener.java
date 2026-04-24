package com.example.ShoppingSystem.service.user.auth.register.risk.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * L6 IP 计数布隆启动监听器。
 * <p>
 * 在应用完成启动后执行一次 L6 布隆初始化：
 * 1) 不阻断 Spring 容器创建流程；
 * 2) 初始化异常时仅记录日志，避免影响主服务可用性。
 */
@Component
public class L6IpCountingBloomInitListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(L6IpCountingBloomInitListener.class);

    private final L6IpCountingBloomInitializerService initializerService;

    public L6IpCountingBloomInitListener(L6IpCountingBloomInitializerService initializerService) {
        this.initializerService = initializerService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        long start = System.currentTimeMillis();
        try {
            initializerService.rebuildL6FiltersOnStartup();
            log.info("L6计数布隆启动初始化执行完成，耗时={}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("L6计数布隆启动初始化失败，但不影响应用继续提供服务", e);
        }
    }
}

