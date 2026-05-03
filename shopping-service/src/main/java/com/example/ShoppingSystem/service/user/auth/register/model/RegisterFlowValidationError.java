package com.example.ShoppingSystem.service.user.auth.register.model;

/**
 * Register flow binding validation errors.
 */
public enum RegisterFlowValidationError {
    MISSING_FLOW_ID,
    EXPIRED,
    PREAUTH_MISMATCH,
    DEVICE_MISMATCH
}
