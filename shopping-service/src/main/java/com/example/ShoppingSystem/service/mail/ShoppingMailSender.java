package com.example.ShoppingSystem.service.mail;

import cn.hutool.core.util.StrUtil;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class ShoppingMailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public ShoppingMailSender(JavaMailSender mailSender,
                              @Value("${app.mail.from-address:${spring.mail.username}}") String fromAddress,
                              @Value("${app.mail.from-name:Shopping System}") String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    public void sendText(String to, String subject, String text) {
        mailSender.send(mimeMessage -> {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, displayName(), "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
        });
    }

    private String displayName() {
        return StrUtil.blankToDefault(fromName, "Shopping System").trim();
    }
}
