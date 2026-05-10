package com.example.ShoppingSystem.admin.controller;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.service.AdminServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.ShoppingSystem.admin.controller")
public class AdminControllerExceptionHandler {

    @ExceptionHandler(AdminServiceException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleAdminServiceException(AdminServiceException ex) {
        HttpStatus status = ex.getStatus() == null ? HttpStatus.BAD_REQUEST : ex.getStatus();
        return ResponseEntity.status(status)
                .body(AdminApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AdminApiResponse<Void>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AdminApiResponse.fail("ADMIN_INTERNAL_ERROR", "管理员服务暂时不可用。"));
    }
}
