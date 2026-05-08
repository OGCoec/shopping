package com.example.ShoppingSystem.service.user.auth.risk;

/**
 * Optional IP-risk cache invalidation hook.
 */
public interface IpRiskCacheInvalidator {

    void invalidateIp(String ip);
}
