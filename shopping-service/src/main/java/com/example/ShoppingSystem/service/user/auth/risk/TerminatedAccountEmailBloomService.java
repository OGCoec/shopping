package com.example.ShoppingSystem.service.user.auth.risk;

public interface TerminatedAccountEmailBloomService {

    String ERROR_ACCOUNT_RISK_TERMINATED = "ACCOUNT_RISK_TERMINATED";
    String MESSAGE_ACCOUNT_RISK_TERMINATED = "账号状态异常，账号已被封禁。";

    void rebuildFromDatabase();

    TerminatedAccountEmailLookupResult lookupEmail(String email);

    boolean isTerminatedEmail(String email);

    void addTerminatedEmailHashAsync(String emailHash);

    record TerminatedAccountEmailLookupResult(boolean terminated,
                                              boolean bloomAvailable,
                                              boolean bloomHit,
                                              boolean dbConfirmed,
                                              String emailHash,
                                              String reasonCode) {
    }
}
