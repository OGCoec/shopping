package com.example.ShoppingSystem.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.client.RedisTimeoutException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisSystemException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SchedulingErrorHandlerTest {

    private final SchedulingErrorHandler handler = new SchedulingErrorHandler();

    @Test
    void logsRedisSystemExceptionDistinctly(CapturedOutput output) {
        handler.handleError(new RedisSystemException("redis fail", new IllegalStateException("wrongtype")));

        assertThat(output.getOut()).contains("Scheduled task Redis failure");
        assertThat(output.getOut()).contains("redis fail");
    }

    @Test
    void logsRedissonTimeoutDistinctly(CapturedOutput output) {
        handler.handleError(new RedisTimeoutException("ping timeout"));

        assertThat(output.getOut()).contains("Scheduled task Redis timeout");
        assertThat(output.getOut()).contains("ping timeout");
    }

    @Test
    void logsOtherScheduledExceptionsAsUnhandled(CapturedOutput output) {
        handler.handleError(new IllegalStateException("boom"));

        assertThat(output.getOut()).contains("Unhandled scheduled task exception");
        assertThat(output.getOut()).contains("boom");
    }
}
