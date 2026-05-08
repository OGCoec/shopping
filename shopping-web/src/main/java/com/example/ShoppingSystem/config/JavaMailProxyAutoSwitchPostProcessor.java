package com.example.ShoppingSystem.config;

import com.example.ShoppingSystem.common.proxy.LocalProxyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Properties;

@Component
public class JavaMailProxyAutoSwitchPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(JavaMailProxyAutoSwitchPostProcessor.class);

    private final LocalProxyResolver localProxyResolver;
    private final boolean enabled;
    private final String configuredHost;
    private final int configuredPort;

    public JavaMailProxyAutoSwitchPostProcessor(
            LocalProxyResolver localProxyResolver,
            @Value("${mail.proxy.auto-switch.enabled:true}") boolean enabled,
            @Value("${spring.mail.properties.mail.smtp.socks.host:127.0.0.1}") String configuredHost,
            @Value("${spring.mail.properties.mail.smtp.socks.port:0}") int configuredPort) {
        this.localProxyResolver = localProxyResolver;
        this.enabled = enabled;
        this.configuredHost = configuredHost;
        this.configuredPort = configuredPort;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!enabled || !(bean instanceof JavaMailSenderImpl mailSender)) {
            return bean;
        }

        LocalProxyResolver.ProxySelection proxySelection =
                localProxyResolver.resolveOrConfigured(configuredHost, configuredPort);
        InetSocketAddress proxyAddress = proxySelection.address();
        if (proxyAddress == null) {
            return bean;
        }

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.socks.host", proxyAddress.getHostString());
        properties.put("mail.smtp.socks.port", String.valueOf(proxyAddress.getPort()));
        log.info("JavaMail SOCKS proxy selected: host={}, port={}, reachable={}, reason={}",
                proxyAddress.getHostString(),
                proxyAddress.getPort(),
                proxySelection.reachable(),
                proxySelection.reason());
        return bean;
    }
}
