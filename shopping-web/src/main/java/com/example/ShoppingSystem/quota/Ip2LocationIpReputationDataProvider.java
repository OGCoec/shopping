package com.example.ShoppingSystem.quota;

import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationDataProvider;
import com.example.ShoppingSystem.service.user.auth.register.risk.IpReputationEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 基于多级查询链的 IP 风险证据提供器。
 */
@Service
@Primary
public class Ip2LocationIpReputationDataProvider implements IpReputationDataProvider {

    private static final Logger log = LoggerFactory.getLogger(Ip2LocationIpReputationDataProvider.class);

    private final IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService;

    public Ip2LocationIpReputationDataProvider(IpReputationMultiLevelQueryService ipReputationMultiLevelQueryService) {
        this.ipReputationMultiLevelQueryService = ipReputationMultiLevelQueryService;
    }

    @Override
    public IpReputationEvidence fetchEvidence(String publicIp) {
        if (publicIp == null || publicIp.isBlank()) {
            log.info("IP风险证据获取：publicIp为空，返回 unavailable");
            return IpReputationEvidence.unavailable();
        }

        IpReputationMultiLevelQueryService.MultiLevelQueryResult result =
                ipReputationMultiLevelQueryService.queryEvidence(publicIp);
        if (result == null || !result.success()) {
            log.info("IP风险多级查询失败：publicIp={}，source={}，reason={}",
                    publicIp,
                    result != null ? result.source() : "NONE",
                    result != null ? result.reason() : "null_result");
            return IpReputationEvidence.unavailable();
        }

        if (result.evidence() == null) {
            if (result.currentScore() == null) {
                log.info("IP风险多级查询无可用分：publicIp={}，source={}，reason={}",
                        publicIp, result.source(), result.reason());
                return IpReputationEvidence.unavailable();
            }
            log.info("IP风险命中score-only：publicIp={}，source={}，score={}",
                    publicIp, result.source(), result.currentScore());
            return IpReputationEvidence.scoreOnly(result.currentScore());
        }

        IpReputationEvidence evidence = result.evidence();
        if (!evidence.hasResolvedScore() && result.currentScore() != null) {
            evidence = evidence.withResolvedScore(result.currentScore());
        }

        log.info(
                "IP风险证据命中：publicIp={}，source={}，score={}，fraudScore={}，usageType={}，proxyType={}，asUsageType={}，addressType={}，"
                        + "flags[tor={},publicProxy={},webProxy={},vpn={},dataCenter={},residentialProxy={},cpn={},epn={}]",
                publicIp,
                result.source(),
                evidence.resolvedScore(),
                evidence.fraudScore(),
                evidence.usageType(),
                evidence.proxyType(),
                evidence.asUsageType(),
                evidence.addressType(),
                evidence.proxyIsTor(),
                evidence.proxyIsPublicProxy(),
                evidence.proxyIsWebProxy(),
                evidence.proxyIsVpn(),
                evidence.proxyIsDataCenter(),
                evidence.proxyIsResidentialProxy(),
                evidence.proxyIsConsumerPrivacyNetwork(),
                evidence.proxyIsEnterprisePrivateNetwork());
        return evidence;
    }
}
