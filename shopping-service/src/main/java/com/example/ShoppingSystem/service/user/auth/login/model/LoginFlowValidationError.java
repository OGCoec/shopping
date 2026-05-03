package com.example.ShoppingSystem.service.user.auth.login.model;

public enum LoginFlowValidationError {
    MISSING_FLOW_ID,
    EXPIRED,
    PREAUTH_MISMATCH,
    DEVICE_MISMATCH
}
