package com.example.ShoppingSystem.common.proxy;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class LocalProxyResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalProxyResolver.class);

    private final boolean enabled;
    private final String defaultHost;
    private final String candidatePorts;
    private final int connectTimeoutMs;

    public LocalProxyResolver(@Value("${local-proxy.enabled:true}") boolean enabled,
                              @Value("${local-proxy.host:127.0.0.1}") String defaultHost,
                              @Value("${local-proxy.ports:7892,7897}") String candidatePorts,
                              @Value("${local-proxy.connect-timeout-ms:300}") int connectTimeoutMs) {
        this.enabled = enabled;
        this.defaultHost = StrUtil.blankToDefault(defaultHost, "127.0.0.1").trim();
        this.candidatePorts = StrUtil.blankToDefault(candidatePorts, "7892,7897");
        this.connectTimeoutMs = Math.max(100, connectTimeoutMs);
    }

    public ProxySelection resolveOrConfigured(String configuredHost, int configuredPort) {
        if (!enabled) {
            return configured(configuredHost, configuredPort, "local_proxy_auto_switch_disabled");
        }

        List<InetSocketAddress> candidates = buildCandidates(configuredHost, configuredPort);
        for (InetSocketAddress candidate : candidates) {
            if (isOpen(candidate)) {
                log.info("Selected local proxy {}:{}", candidate.getHostString(), candidate.getPort());
                return new ProxySelection(candidate, true, "reachable");
            }
        }

        ProxySelection fallback = configured(configuredHost, configuredPort, "no_reachable_candidate");
        if (fallback.address() != null) {
            log.warn("No configured local proxy candidate is reachable. Falling back to configured proxy {}:{}",
                    fallback.address().getHostString(),
                    fallback.address().getPort());
        } else {
            log.warn("No configured local proxy candidate is reachable and no configured proxy fallback exists.");
        }
        return fallback;
    }

    private List<InetSocketAddress> buildCandidates(String configuredHost, int configuredPort) {
        Set<String> seen = new LinkedHashSet<>();
        List<InetSocketAddress> candidates = new ArrayList<>();
        addCandidate(candidates, seen, configuredHost, configuredPort);
        for (String rawPort : candidatePorts.split(",")) {
            int port = parsePort(rawPort);
            addCandidate(candidates, seen, defaultHost, port);
        }
        return candidates;
    }

    private void addCandidate(List<InetSocketAddress> candidates,
                              Set<String> seen,
                              String host,
                              int port) {
        if (StrUtil.isBlank(host) || port <= 0 || port > 65535) {
            return;
        }
        String normalizedHost = host.trim();
        String key = normalizedHost + ":" + port;
        if (seen.add(key)) {
            candidates.add(new InetSocketAddress(normalizedHost, port));
        }
    }

    private ProxySelection configured(String configuredHost, int configuredPort, String reason) {
        if (StrUtil.isBlank(configuredHost) || configuredPort <= 0 || configuredPort > 65535) {
            return new ProxySelection(null, false, reason);
        }
        return new ProxySelection(
                new InetSocketAddress(configuredHost.trim(), configuredPort),
                false,
                reason);
    }

    private boolean isOpen(InetSocketAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(address, connectTimeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private int parsePort(String rawPort) {
        if (StrUtil.isBlank(rawPort)) {
            return -1;
        }
        try {
            return Integer.parseInt(rawPort.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public Duration connectTimeout() {
        return Duration.ofMillis(connectTimeoutMs);
    }

    public record ProxySelection(InetSocketAddress address, boolean reachable, String reason) {
    }
}
