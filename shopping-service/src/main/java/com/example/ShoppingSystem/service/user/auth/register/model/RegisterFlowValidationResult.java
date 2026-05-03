package com.example.ShoppingSystem.service.user.auth.register.model;

/**
 * Result for register flow binding validation.
 */
public record RegisterFlowValidationResult(boolean valid,
                                           RegisterFlowValidationError error,
                                           RegisterFlowSession session) {

    public static RegisterFlowValidationResult valid(RegisterFlowSession session) {
        return new RegisterFlowValidationResult(true, null, session);
    }

    public static RegisterFlowValidationResult invalid(RegisterFlowValidationError error) {
        return new RegisterFlowValidationResult(false, error, null);
    }
}
