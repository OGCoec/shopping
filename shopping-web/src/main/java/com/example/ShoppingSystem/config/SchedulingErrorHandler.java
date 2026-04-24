package com.example.ShoppingSystem.config;

import org.redisson.client.RedisTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

@Component
public class SchedulingErrorHandler implements ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(SchedulingErrorHandler.class);

    @Override
    public void handleError(Throwable t) {
        if (t instanceof RedisSystemException e) {
            log.error("Scheduled task Redis failure: {}", e.getMessage(), e);
            return;
        }
        if (t instanceof RedisTimeoutException e) {
            log.error("Scheduled task Redis timeout: {}", e.getMessage(), e);
            return;
        }
        log.error("Unhandled scheduled task exception: {}", t.getMessage(), t);
    }
}
