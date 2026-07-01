package com.example.ShoppingSystem.quota.writeback;

public interface IpRiskWritebackIdempotencyService {
    public boolean markProcessing(String eventId);

    public void clearProcessing(String eventId);
}
