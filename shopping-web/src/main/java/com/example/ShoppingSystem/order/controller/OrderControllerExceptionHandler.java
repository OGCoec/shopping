package com.example.ShoppingSystem.order.controller;

import com.example.ShoppingSystem.order.dto.OrderApiResponse;
import com.example.ShoppingSystem.order.service.OrderServiceException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {OrderController.class, OrderRefundController.class, OrderPaymentCallbackController.class})
public class OrderControllerExceptionHandler {

    @ExceptionHandler(OrderServiceException.class)
    public ResponseEntity<OrderApiResponse<Void>> handleOrderException(OrderServiceException exception) {
        return ResponseEntity.status(exception.status())
                .body(OrderApiResponse.fail(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<OrderApiResponse<Void>> handleStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus responseStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String code = exception.getReason() == null || exception.getReason().isBlank()
                ? "ORDER_REQUEST_FAILED"
                : exception.getReason();
        return ResponseEntity.status(responseStatus)
                .body(OrderApiResponse.fail(code, code));
    }
}
