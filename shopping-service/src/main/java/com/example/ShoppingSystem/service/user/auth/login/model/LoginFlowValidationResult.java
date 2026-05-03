package com.example.ShoppingSystem.service.user.auth.login.model;

public record LoginFlowValidationResult(
        boolean valid,
        LoginFlowSession session,
        LoginFlowValidationError error
) {

    public static LoginFlowValidationResult valid(LoginFlowSession session) {
        return new LoginFlowValidationResult(true, session, null);
    }

    public static LoginFlowValidationResult invalid(LoginFlowValidationError error) {
        return new LoginFlowValidationResult(false, null, error);
    }
}
