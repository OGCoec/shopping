package com.example.ShoppingSystem.quota.writeback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves writeback action plan by source using configuration-driven mapping.
 */
@Component
public class IpRiskWritebackPlanResolver {

    private static final Logger log = LoggerFactory.getLogger(IpRiskWritebackPlanResolver.class);

    private final IpRiskWritebackProperties properties;

    public IpRiskWritebackPlanResolver(IpRiskWritebackProperties properties) {
        this.properties = properties;
    }

    public Set<IpRiskWritebackAction> resolveActions(String source) {
        String normalizedSource = normalize(source);
        Map<String, String> mapping = properties.getSourceActions();
        String raw = null;
        if (mapping != null) {
            raw = mapping.get(normalizedSource);
            if (raw == null) {
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    if (entry.getKey() != null && normalizedSource.equalsIgnoreCase(entry.getKey())) {
                        raw = entry.getValue();
                        break;
                    }
                }
            }
        }
        if (raw == null || raw.isBlank()) {
            return EnumSet.noneOf(IpRiskWritebackAction.class);
        }

        EnumSet<IpRiskWritebackAction> actions = EnumSet.noneOf(IpRiskWritebackAction.class);
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                actions.add(IpRiskWritebackAction.valueOf(token.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                log.warn("IP风险回写策略中存在未知动作，source={}，action={}", normalizedSource, token.trim());
            }
        }
        return actions;
    }

    public IpRiskWritebackMode resolveMode() {
        return IpRiskWritebackMode.fromString(properties.getMode());
    }

    private String normalize(String source) {
        if (source == null || source.isBlank()) {
            return "NONE";
        }
        return source.trim().toUpperCase();
    }
}
