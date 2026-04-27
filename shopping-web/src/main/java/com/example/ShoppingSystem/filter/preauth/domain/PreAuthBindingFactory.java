package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthRiskProfile;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责创建和刷新预登录绑定对象。
 * <p>
 * 它定义了绑定对象如何“长出来”和“随请求变化而演化”，
 * 包括 TTL、recentIps、changeCount、风险分重算等规则。
 */
@Component
public class PreAuthBindingFactory {

    private final PreAuthProperties properties;
    private final PreAuthRiskService riskService;

    public PreAuthBindingFactory(PreAuthProperties properties,
                                 PreAuthRiskService riskService) {
        this.properties = properties;
        this.riskService = riskService;
    }

    /**
     * 创建一个全新的绑定对象。
     */
    public PreAuthBinding createNewBinding(String token,
                                           String fpHash,
                                           String uaHash,
                                           String currentIp) {
        long now = System.currentTimeMillis();

        // 新绑定创建时，直接按当前 IP 计算风险信息。
        PreAuthRiskProfile riskProfile = riskService.resolveRiskProfile(currentIp);
        return new PreAuthBinding(
                token,
                fpHash,
                uaHash,
                currentIp,
                appendRecentIp(new ArrayList<>(), currentIp),
                0,
                now,
                now + bindingTtl().toMillis(),
                riskProfile.score(),
                riskProfile.riskLevel()
        );
    }

    /**
     * 在已有绑定基础上刷新上下文。
     * <p>
     * 如果 IP 变化，会同步更新 recentIps、changeCount，并重新计算风险分与风险等级。
     */
    public PreAuthBinding refreshExistingBinding(PreAuthBinding existing, String currentIp) {
        long now = System.currentTimeMillis();
        boolean ipChanged = !StrUtil.equals(existing.currentIp(), currentIp);
        int changeCount = existing.changeCount();
        List<String> recentIps = existing.recentIps() == null
                ? new ArrayList<>()
                : new ArrayList<>(existing.recentIps());
        String riskLevel = existing.riskLevel();
        int score = existing.score();

        if (ipChanged) {
            // 把新 IP 推到 recentIps 最前面，并去重裁剪。
            recentIps = appendRecentIp(recentIps, currentIp);
            changeCount += 1;

            // IP 变化后重新走一次风控计算。
            PreAuthRiskProfile riskProfile = riskService.resolveRiskProfile(currentIp);
            score = riskProfile.score();
            riskLevel = riskProfile.riskLevel();
        }

        return new PreAuthBinding(
                existing.token(),
                existing.fpHash(),
                existing.uaHash(),
                currentIp,
                recentIps,
                changeCount,
                now,
                now + bindingTtl().toMillis(),
                score,
                riskLevel
        );
    }

    /**
     * 返回绑定对象的统一 TTL。
     */
    public Duration bindingTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getTtlMinutes()));
    }

    /**
     * 维护 recentIps 列表。
     * <p>
     * 规则：
     * 1) 为空时初始化；
     * 2) 先去掉旧位置上的同 IP；
     * 3) 再把新 IP 头插；
     * 4) 最后按配置裁剪长度。
     */
    private List<String> appendRecentIp(List<String> recentIps, String ip) {
        if (recentIps == null) {
            recentIps = new ArrayList<>();
        }
        if (StrUtil.isBlank(ip)) {
            return recentIps;
        }

        // 去掉旧位置上的相同 IP，避免 recentIps 出现重复。
        recentIps.removeIf(existing -> StrUtil.equals(existing, ip));

        // 最新访问 IP 始终放在最前面，方便后续快速观察。
        recentIps.add(0, ip);
        int safeLimit = Math.max(1, properties.getRecentIpLimit());
        while (recentIps.size() > safeLimit) {
            // 超出上限时从尾部移除最旧的记录。
            recentIps.remove(recentIps.size() - 1);
        }
        return recentIps;
    }
}
