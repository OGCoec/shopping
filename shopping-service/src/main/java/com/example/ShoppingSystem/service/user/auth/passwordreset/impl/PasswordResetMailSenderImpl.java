package com.example.ShoppingSystem.service.user.auth.passwordreset.impl;

import com.example.ShoppingSystem.service.mail.ShoppingMailSender;
import com.example.ShoppingSystem.service.user.auth.passwordreset.PasswordResetMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class PasswordResetMailSenderImpl implements PasswordResetMailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final ShoppingMailSender shoppingMailSender;

    public PasswordResetMailSenderImpl(JavaMailSender mailSender,
                                       @Value("${spring.mail.username}") String fromAddress,
                                       ShoppingMailSender shoppingMailSender) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.shoppingMailSender = shoppingMailSender;
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendResetCode(String email, String code, long expireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Password Reset Verification Code");
        message.setText(String.format(
                "Your password reset verification code is %s. It expires in %d minutes.",
                code,
                expireMinutes));
        mailSender.send(message);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendResetLink(String email, String resetUrl, long expireMinutes) {
        shoppingMailSender.sendHtmlAlternative(
                email,
                "Password Reset Link",
                String.format(
                        "Please open this email in an HTML-capable email client and click the reset password button. "
                                + "This link expires in %d minutes.",
                        expireMinutes),
                buildResetLinkHtml(resetUrl, expireMinutes));
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendResetCodeAndLink(String email,
                                     String code,
                                     String resetUrl,
                                     long codeExpireMinutes,
                                     long linkExpireMinutes) {
        shoppingMailSender.sendHtmlAlternative(
                email,
                "Password Reset Verification",
                String.format(
                        "Your password reset verification code is %s. It expires in %d minutes.%n%n"
                                + "You can also open this email in an HTML-capable email client and click the reset password button. "
                                + "This link expires in %d minutes.",
                        code,
                        codeExpireMinutes,
                        linkExpireMinutes),
                buildResetCodeAndLinkHtml(code, resetUrl, codeExpireMinutes, linkExpireMinutes));
    }

    private String buildResetLinkHtml(String resetUrl, long expireMinutes) {
        return String.format(
                """
                        <!doctype html>
                        <html>
                        <body style="margin:0;padding:24px;background:#f6f7fb;font-family:Arial,Helvetica,sans-serif;color:#202124;">
                          <div style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;">
                            <h1 style="margin:0 0 16px;font-size:22px;line-height:1.3;color:#111827;">Reset your password</h1>
                            <p style="margin:0 0 20px;font-size:15px;line-height:1.6;">We received a request to reset your Shopping System password.</p>
                            <p style="margin:0 0 22px;">
                              <a href="%s" style="display:inline-block;padding:12px 18px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;font-size:15px;font-weight:700;">重置密码</a>
                            </p>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#4b5563;">This link expires in %s minutes.</p>
                            <p style="margin:0;font-size:13px;line-height:1.6;color:#6b7280;">If you did not request a password reset, you can ignore this email.</p>
                          </div>
                        </body>
                        </html>
                        """,
                html(resetUrl),
                html(String.valueOf(expireMinutes)));
    }

    private String buildResetCodeAndLinkHtml(String code,
                                             String resetUrl,
                                             long codeExpireMinutes,
                                             long linkExpireMinutes) {
        return String.format(
                """
                        <!doctype html>
                        <html>
                        <body style="margin:0;padding:24px;background:#f6f7fb;font-family:Arial,Helvetica,sans-serif;color:#202124;">
                          <div style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;">
                            <h1 style="margin:0 0 16px;font-size:22px;line-height:1.3;color:#111827;">Reset your password</h1>
                            <p style="margin:0 0 12px;font-size:15px;line-height:1.6;">Your password reset verification code is:</p>
                            <div style="display:inline-block;margin:0 0 18px;padding:10px 14px;background:#f3f4f6;border-radius:6px;font-size:24px;font-weight:700;letter-spacing:4px;color:#111827;">%s</div>
                            <p style="margin:0 0 20px;font-size:14px;line-height:1.6;color:#4b5563;">The code expires in %s minutes. You can also reset your password with the button below.</p>
                            <p style="margin:0 0 22px;">
                              <a href="%s" style="display:inline-block;padding:12px 18px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;font-size:15px;font-weight:700;">重置密码</a>
                            </p>
                            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#4b5563;">This link expires in %s minutes.</p>
                            <p style="margin:0;font-size:13px;line-height:1.6;color:#6b7280;">If you did not request a password reset, you can ignore this email.</p>
                          </div>
                        </body>
                        </html>
                        """,
                html(code),
                html(String.valueOf(codeExpireMinutes)),
                html(resetUrl),
                html(String.valueOf(linkExpireMinutes)));
    }

    private String html(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
