package com.example.ShoppingSystem.service.user.auth.register.risk.impl;

import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationDataProvider;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
import org.springframework.stereotype.Service;

/**
 * 保底实现：当上层模块未提供真实数据源时，返回 unavailable。
 * <p>
 * 设计目的：
 * 1) 保证 shopping-service 单模块可独立启动、可独立测试；
 * 2) 避免因为 web 侧外部依赖不可用导致 Spring 装配失败；
 * 3) 把“是否有真实证据源”降级为评分结果差异，而不是系统可用性问题。
 */
@Service
public class FallbackIpReputationDataProvider implements IpReputationDataProvider {

    /**
     * 固定返回 unavailable，交由评分服务走保底分数。
     */
    @Override
    public IpReputationEvidence fetchEvidence(String publicIp) {
        return IpReputationEvidence.unavailable();
    }
}
