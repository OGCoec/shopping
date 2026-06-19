package com.example.ShoppingSystem.config.datasource;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Service
public class ClientIpResolver {

    public Optional<String> resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return firstValidForwardedFor(request.getHeader("X-Forwarded-For"))
                .or(() -> validIp(request.getHeader("X-Real-IP")))
                .or(() -> validIp(request.getRemoteAddr()));
    }

    private Optional<String> firstValidForwardedFor(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (String part : value.split(",")) {
            Optional<String> ip = validIp(part);
            if (ip.isPresent()) {
                return ip;
            }
        }
        return Optional.empty();
    }

    private Optional<String> validIp(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || "unknown".equalsIgnoreCase(normalized)) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }
}
