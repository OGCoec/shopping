package com.example.ShoppingSystem.admin.service.auth;

public interface AdminEmailCodeService {
    public void sendFirstLoginEmailCode(String email);

    public void verifyAndClear(String email, String code);

    public long ttlSeconds();

    public long cooldownSeconds();
}
