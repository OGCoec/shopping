package com.example.ShoppingSystem.filter.preauth.domain;

import cn.hutool.core.util.StrUtil;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthIpNormalizer;
import com.example.ShoppingSystem.filter.preauth.support.PreAuthProperties;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class TrustedExitIpMatcher {

    private final PreAuthProperties properties;

    public TrustedExitIpMatcher(PreAuthProperties properties) {
        this.properties = properties;
    }

    public boolean isTrustedMatch(String httpIp, Collection<String> webRtcIps) {
        String normalizedHttpIp = PreAuthIpNormalizer.normalizeIp(httpIp);
        if (StrUtil.isBlank(normalizedHttpIp) || webRtcIps == null || webRtcIps.isEmpty()) {
            return false;
        }

        Set<String> normalizedWebRtcIps = new LinkedHashSet<>();
        for (String candidateIp : webRtcIps) {
            String normalizedCandidateIp = PreAuthIpNormalizer.normalizeIp(candidateIp);
            if (StrUtil.isNotBlank(normalizedCandidateIp)) {
                normalizedWebRtcIps.add(normalizedCandidateIp);
            }
        }
        if (normalizedWebRtcIps.isEmpty()) {
            return false;
        }

        for (String groupSpec : properties.getTrustedExitIpGroups()) {
            Set<String> group = parseGroup(groupSpec);
            if (!group.contains(normalizedHttpIp)) {
                continue;
            }
            for (String candidateIp : normalizedWebRtcIps) {
                if (group.contains(candidateIp)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> parseGroup(String groupSpec) {
        Set<String> group = new LinkedHashSet<>();
        if (StrUtil.isBlank(groupSpec)) {
            return group;
        }
        String[] parts = groupSpec.split("[,\\s]+");
        for (String part : parts) {
            String normalizedIp = PreAuthIpNormalizer.normalizeIp(part);
            if (StrUtil.isNotBlank(normalizedIp)) {
                group.add(normalizedIp);
            }
        }
        return group;
    }
}
