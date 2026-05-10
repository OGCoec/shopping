package com.example.ShoppingSystem.tools.ip2location.verify;

import com.example.ShoppingSystem.common.proxy.LocalProxyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

final class Ip2LocationVerifyMailMainSupport {

    private static final String DEFAULT_PROXY_HOST = "127.0.0.1";
    private static final int DEFAULT_PROXY_PORT = 7892;
    private static final String CANDIDATE_PROXY_PORTS = "7892,7897";
    private static final int PROXY_CONNECT_TIMEOUT_MS = 300;

    private Ip2LocationVerifyMailMainSupport() {
    }

    static void configureUtf8Console() {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    static Ip2LocationVerifyMailReaderService createMailReaderService() {
        LocalProxyResolver.ProxySelection proxySelection = new LocalProxyResolver(
                true,
                DEFAULT_PROXY_HOST,
                CANDIDATE_PROXY_PORTS,
                PROXY_CONNECT_TIMEOUT_MS
        ).resolveOrConfigured(DEFAULT_PROXY_HOST, DEFAULT_PROXY_PORT);
        InetSocketAddress proxyAddress = proxySelection.address();
        String proxyHost = proxyAddress == null ? DEFAULT_PROXY_HOST : proxyAddress.getHostString();
        int proxyPort = proxyAddress == null ? DEFAULT_PROXY_PORT : proxyAddress.getPort();

        System.out.println("IMAP SOCKS代理: " + proxyHost + ":" + proxyPort
                + "，可连接=" + proxySelection.reachable()
                + "，原因=" + proxySelection.reason());

        return new Ip2LocationVerifyMailReaderService(
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build(),
                "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                "imap-mail.outlook.com",
                993,
                "https://outlook.office.com/IMAP.AccessAsUser.All offline_access",
                20,
                List.of("Junk Email", "INBOX"),
                "ip2location.io",
                "ip2location",
                proxyHost,
                proxyPort,
                0
        );
    }
}
