package com.example.ShoppingSystem.controller;

import com.example.ShoppingSystem.common.exception.TianaiCaptchaFormatException;
import org.redisson.client.RedisTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void missingRequestParameterReturns400Payload() throws Exception {
        mockMvc.perform(get("/test/missing").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: name"))
                .andExpect(jsonPath("$.path").value("/test/missing"));
    }

    @Test
    void illegalArgumentReturns400Payload() throws Exception {
        mockMvc.perform(get("/test/illegal").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("bad argument"))
                .andExpect(jsonPath("$.path").value("/test/illegal"));
    }

    @Test
    void unhandledExceptionReturns500Payload() throws Exception {
        mockMvc.perform(get("/test/error").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.path").value("/test/error"));
    }

    @Test
    void tianaiFormatExceptionReturns400Payload() throws Exception {
        mockMvc.perform(get("/test/tianai-format").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("TIANAI_CAPTCHA_FORMAT_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid Tianai captcha payload"))
                .andExpect(jsonPath("$.path").value("/test/tianai-format"));
    }

    @Test
    void redisSystemExceptionReturns503Payload() throws Exception {
        mockMvc.perform(get("/test/redis-system").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("REDIS_ERROR"))
                .andExpect(jsonPath("$.message").value("Redis operation failed"))
                .andExpect(jsonPath("$.path").value("/test/redis-system"));
    }

    @Test
    void redissonTimeoutExceptionReturns503Payload() throws Exception {
        mockMvc.perform(get("/test/redis-timeout").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("REDIS_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("Redis operation timed out"))
                .andExpect(jsonPath("$.path").value("/test/redis-timeout"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/missing")
        String missing(@RequestParam("name") String name) {
            return name;
        }

        @GetMapping("/illegal")
        String illegal() {
            throw new IllegalArgumentException("bad argument");
        }

        @GetMapping("/error")
        String error() {
            throw new RuntimeException("boom");
        }

        @GetMapping("/tianai-format")
        String tianaiFormat() {
            throw new TianaiCaptchaFormatException("Invalid Tianai captcha payload");
        }

        @GetMapping("/redis-system")
        String redisSystem() {
            throw new RedisSystemException("redis fail", new IllegalStateException("wrongtype"));
        }

        @GetMapping("/redis-timeout")
        String redisTimeout() {
            throw new RedisTimeoutException("timeout");
        }
    }
}
