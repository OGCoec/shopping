package com.example.ShoppingSystem.controller;

import com.example.ShoppingSystem.common.exception.TianaiCaptchaFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.redisson.client.RedisTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.EOFException;
import java.time.OffsetDateTime;

@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<Void> handleClientAbortException(ClientAbortException e,
                                                           HttpServletRequest request) {
        log.debug("Client aborted connection on path {}: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GlobalErrorResponse> handleMissingParameter(MissingServletRequestParameterException e,
                                                                      HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "Missing required parameter: " + e.getParameterName(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalErrorResponse> handleInvalidRequestBody(HttpMessageNotReadableException e,
                                                                        HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing or malformed",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GlobalErrorResponse> handleIllegalArgument(IllegalArgumentException e,
                                                                     HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                e.getMessage() == null || e.getMessage().isBlank() ? "Invalid argument" : e.getMessage(),
                request
        );
    }

    @ExceptionHandler(TianaiCaptchaFormatException.class)
    public ResponseEntity<GlobalErrorResponse> handleTianaiCaptchaFormat(TianaiCaptchaFormatException e,
                                                                         HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "TIANAI_CAPTCHA_FORMAT_ERROR",
                e.getMessage() == null || e.getMessage().isBlank()
                        ? "Invalid Tianai captcha payload"
                        : e.getMessage(),
                request
        );
    }

    @ExceptionHandler(RedisSystemException.class)
    public ResponseEntity<GlobalErrorResponse> handleRedisSystemException(RedisSystemException e,
                                                                          HttpServletRequest request) {
        log.warn("Redis system exception on path {}: {}", request.getRequestURI(), e.getMessage());
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "REDIS_ERROR",
                "Redis operation failed",
                request
        );
    }

    @ExceptionHandler(RedisTimeoutException.class)
    public ResponseEntity<GlobalErrorResponse> handleRedisTimeoutException(RedisTimeoutException e,
                                                                           HttpServletRequest request) {
        log.warn("Redis timeout on path {}: {}", request.getRequestURI(), e.getMessage());
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "REDIS_TIMEOUT",
                "Redis operation timed out",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalErrorResponse> handleUnhandledException(Exception e,
                                                                        HttpServletRequest request) {
        if (isClientAbortRelated(e)) {
            log.debug("Client aborted connection on path {}: {}", request.getRequestURI(), e.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        log.error("Unhandled exception on path {}", request.getRequestURI(), e);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                request
        );
    }

    private ResponseEntity<GlobalErrorResponse> buildResponse(HttpStatus status,
                                                              String error,
                                                              String message,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GlobalErrorResponse(
                false,
                status.value(),
                error,
                message,
                request.getRequestURI(),
                OffsetDateTime.now().toString()
        ));
    }

    private boolean isClientAbortRelated(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClientAbortException
                    || current instanceof EOFException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset by peer")
                        || normalized.contains("forcibly closed")
                        || normalized.contains("an established connection was aborted by the software in your host machine")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}

