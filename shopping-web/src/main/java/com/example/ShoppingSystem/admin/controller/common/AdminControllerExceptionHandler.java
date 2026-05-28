package com.example.ShoppingSystem.admin.controller.common;

import com.example.ShoppingSystem.admin.dto.AdminApiResponse;
import com.example.ShoppingSystem.admin.service.common.AdminServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.example.ShoppingSystem.admin.controller")
public class AdminControllerExceptionHandler {

    @ExceptionHandler(AdminServiceException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleAdminServiceException(AdminServiceException ex) {
        HttpStatus status = ex.getStatus() == null ? HttpStatus.BAD_REQUEST : ex.getStatus();
        return ResponseEntity.status(status)
                .body(AdminApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String parameterName = ex.getName();
        String message = "Request parameter has invalid type.";
        if ("pageSize".equals(parameterName)) {
            message = "pageSize must be a positive integer.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AdminApiResponse.fail("ADMIN_REQUEST_PARAM_INVALID", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AdminApiResponse<Void>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AdminApiResponse.fail("ADMIN_INTERNAL_ERROR", "管理员服务暂时不可用。"));
    }
}
