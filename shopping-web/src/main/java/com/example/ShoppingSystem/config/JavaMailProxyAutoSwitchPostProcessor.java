package com.example.ShoppingSystem.config;

import com.example.ShoppingSystem.common.proxy.OutboundRouteResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class JavaMailProxyAutoSwitchPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(JavaMailProxyAutoSwitchPostProcessor.class);

    private final OutboundRouteResolver outboundRouteResolver;
    private final boolean enabled;
    private final String configuredHost;
    private final int configuredPort;
    private final String smtpHost;
    private final int smtpPort;
    private final String routeMode;
    private final int routeProbeTimeoutMs;

    public JavaMailProxyAutoSwitchPostProcessor(
            OutboundRouteResolver outboundRouteResolver,
            @Value("${mail.proxy.auto-switch.enabled:true}") boolean enabled,
            @Value("${spring.mail.properties.mail.smtp.socks.host:127.0.0.1}") String configuredHost,
            @Value("${spring.mail.properties.mail.smtp.socks.port:0}") int configuredPort,
            @Value("${spring.mail.host:smtp.qq.com}") String smtpHost,
            @Value("${spring.mail.port:587}") int smtpPort,
            @Value("${mail.proxy.route-mode:auto}") String routeMode,
            @Value("${mail.proxy.route-probe-timeout-ms:1500}") int routeProbeTimeoutMs) {
        this.outboundRouteResolver = outboundRouteResolver;
        this.enabled = enabled;
        this.configuredHost = configuredHost;
        this.configuredPort = configuredPort;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.routeMode = routeMode;
        this.routeProbeTimeoutMs = routeProbeTimeoutMs;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!enabled || !(bean instanceof JavaMailSenderImpl mailSender)) {
            return bean;
        }

        OutboundRouteResolver.RouteSelection routeSelection = outboundRouteResolver.selectRoute(
                "JavaMail SMTP",
                smtpHost,
                smtpPort,
                configuredHost,
                configuredPort,
                routeMode,
                routeProbeTimeoutMs,
                OutboundRouteResolver.ProxyProtocol.SOCKS
        );
        Properties properties = mailSender.getJavaMailProperties();
        if (routeSelection.direct()) {
            properties.remove("mail.smtp.socks.host");
            properties.remove("mail.smtp.socks.port");
            log.info("JavaMail SMTP DIRECT route selected, target={}:{}, reachable={}, reason={}",
                    smtpHost,
                    smtpPort,
                    routeSelection.reachable(),
                    routeSelection.reason());
            return bean;
        }
        properties.put("mail.smtp.socks.host", routeSelection.host());
        properties.put("mail.smtp.socks.port", String.valueOf(routeSelection.port()));
        log.info("JavaMail SMTP SOCKS proxy selected: host={}, port={}, target={}:{}, reachable={}, reason={}",
                routeSelection.host(),
                routeSelection.port(),
                smtpHost,
                smtpPort,
                routeSelection.reachable(),
                routeSelection.reason());
        return bean;
    }
}
